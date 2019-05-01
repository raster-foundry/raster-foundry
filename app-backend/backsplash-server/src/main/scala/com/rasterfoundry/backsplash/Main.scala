package com.rasterfoundry.backsplash.server

import com.rasterfoundry.database.{
  LayerAttributeDao,
  ProjectDao,
  SceneDao,
  SceneToLayerDao,
  ToolRunDao
}
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.backsplash.MosaicImplicits
import com.rasterfoundry.database.util.RFTransactor

import cats.data.OptionT
import cats.effect._
import cats.implicits._
import com.olegpy.meow.hierarchy._
import org.http4s._
import org.http4s.server.middleware.{AutoSlash, CORS, CORSConfig, Timeout}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.Router
import org.http4s.syntax.kleisli._
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.typesafe.scalalogging.LazyLogging
import doobie.implicits._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.util.Properties
import java.util.concurrent.{Executors, TimeUnit}

object Main extends IOApp with HistogramStoreImplicits with LazyLogging {

  val xaConfig = RFTransactor.TransactorConfig(
    contextShift = IO.contextShift(
      ExecutionContext.fromExecutor(
        Executors.newFixedThreadPool(
          Config.parallelism.dbThreadPoolSize,
          new ThreadFactoryBuilder().setNameFormat("db-client-%d").build()
        )
      )
    )
  )

  val xa = RFTransactor.buildTransactor(xaConfig)

  val ogcUrlPrefix = Properties.envOrNone("ENVIRONMENT") match {
    case Some("Production") => "https://tiles.rasterfoundry.com"
    case Some("Staging")    => "https://tiles.staging.rasterfoundry.com"
    case _                  => "http://localhost:8081"
  }

  override protected implicit def contextShift: ContextShift[IO] =
    IO.contextShift(
      ExecutionContext.fromExecutor(
        Executors.newFixedThreadPool(
          Config.parallelism.http4sThreadPoolSize,
          new ThreadFactoryBuilder().setNameFormat("http4s-%d").build()
        )
      )
    )

  val blazeEC = ExecutionContext.fromExecutor(
    Executors.newFixedThreadPool(
      Config.parallelism.blazeThreadPoolSize,
      new ThreadFactoryBuilder().setNameFormat("blaze-cached-%d").build()
    )
  )

  val projectStoreImplicits = new ProjectStoreImplicits(xa)

  val timeout: FiniteDuration =
    new FiniteDuration(Config.server.timeoutSeconds, TimeUnit.SECONDS)

  val backsplashErrorHandler: HttpErrorHandler[IO, BacksplashException] =
    new BacksplashHttpErrorHandler[IO]

  val foreignErrorHandler: HttpErrorHandler[IO, Throwable] =
    new ForeignErrorHandler[IO, Throwable]

  val rollbarReporter: RollbarReporter[IO] =
    new RollbarReporter()

  def withCORS(svc: HttpRoutes[IO]): HttpRoutes[IO] =
    CORS(
      svc,
      CORSConfig(
        anyOrigin = true,
        anyMethod = false,
        allowedMethods = Some(Set("GET", "POST", "HEAD", "OPTIONS")),
        allowedHeaders = Some(Set("Content-Type", "Authorization", "*")),
        allowCredentials = true,
        maxAge = 1800
      )
    )

  def withTimeout(service: HttpRoutes[IO]): HttpRoutes[IO] =
    Timeout(
      timeout,
      OptionT.pure[IO](Response[IO](Status.GatewayTimeout))
    )(service)

  def errorHandling(service: HttpRoutes[IO]): HttpRoutes[IO] =
    backsplashErrorHandler.handle {
      rollbarReporter.handle {
        foreignErrorHandler.handle {
          service
        }
      }
    }

  val authenticators = new Authenticators(xa)

  val mosaicImplicits = new MosaicImplicits(LayerAttributeDao())
  val toolStoreImplicits = new ToolStoreImplicits(mosaicImplicits, xa)
  import toolStoreImplicits._
  val ogcImplicits = new OgcImplicits(SceneToLayerDao(), xa)
  import ogcImplicits._

  val analysisManager =
    new AnalysisManager(ToolRunDao(), mosaicImplicits, toolStoreImplicits, xa)

  val mosaicService: HttpRoutes[IO] =
    authenticators.tokensAuthMiddleware(
      AuthedAutoSlash(
        new MosaicService(
          SceneToLayerDao(),
          mosaicImplicits,
          analysisManager,
          xa
        ).routes
      )
    )

  val analysisService: HttpRoutes[IO] =
    authenticators.tokensAuthMiddleware(
      AuthedAutoSlash(new AnalysisService(analysisManager).routes)
    )

  val sceneMosaicService: HttpRoutes[IO] =
    authenticators.tokensAuthMiddleware(
      AuthedAutoSlash(
        new SceneService(SceneDao(), mosaicImplicits, xa).routes
      )
    )

  val wcsService = authenticators.tokensAuthMiddleware(
    AuthedAutoSlash(
      new WcsService(ProjectDao(), ogcUrlPrefix).routes
    )
  )

  val wmsService = authenticators.tokensAuthMiddleware(
    AuthedAutoSlash(
      new WmsService(ProjectDao(), ogcUrlPrefix).routes
    )
  )

  val httpApp = errorHandling {
    Router(
      "/" -> ProjectToProjectLayerMiddleware(
        withCORS(withTimeout(mosaicService)),
        xa
      ),
      "/scenes" -> withCORS(withTimeout(sceneMosaicService)),
      "/tools" -> withCORS(withTimeout(analysisService)),
      "/wcs" -> withCORS(withTimeout(wcsService)),
      "/wms" -> withCORS(withTimeout(wmsService)),
      "/healthcheck" -> AutoSlash(new HealthcheckService(xa).routes)
    )
  }

  val startupBanner =
    """|    ___                     _               _ __     _                     _
       |   | _ )   __ _     __     | |__    ___    | '_ \   | |    __ _     ___   | |_
       |   | _ \  / _` |   / _|    | / /   (_-<    | .__/   | |   / _` |   (_-<   | ' \
       |   |___/  \__,_|   \__|_   |_\_\   /__/_   |_|__   _|_|_  \__,_|   /__/_  |_||_|
       | _|'''''|_|'''''|_|'''''|_|'''''|_|'''''|_|'''''|_|'''''|_|'''''|_|'''''|_|'''''|
       | '`-0-0-''`-0-0-''`-0-0-''`-0-0-''`-0-0-''`-0-0-''`-0-0-''`-0-0-''`-0-0-''`-0-0-'""".stripMargin
      .split("\n")
      .toList

  def stream =
    BlazeServerBuilder[IO]
      .withExecutionContext(blazeEC)
      .withBanner(startupBanner)
      .withConnectorPoolSize(Config.parallelism.blazeConnectorPoolSize)
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(httpApp.orNotFound)
      .serve

  val canSelect = sql"SELECT 1".query[Int].unique.transact(xa).unsafeRunSync
  logger.info(s"Server Started (${canSelect})")

  def run(args: List[String]): IO[ExitCode] =
    for {
      exit <- stream.compile.drain.map(_ => ExitCode.Success)
    } yield exit
}
