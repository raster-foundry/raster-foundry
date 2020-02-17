package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.MosaicImplicits
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.common.{Config => CommonConfig}
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.database.{
  LayerAttributeDao,
  ProjectDao,
  SceneToLayerDao,
  ToolRunDao
}
import com.rasterfoundry.http4s.{JaegerTracer, XRayTracer}

import cats.data.OptionT
import cats.effect._
import com.colisweb.tracing.TracingContext.TracingContextBuilder
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.olegpy.meow.hierarchy._
import com.typesafe.scalalogging.LazyLogging
import doobie.implicits._
import org.http4s._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.{CORS, CORSConfig, Timeout}
import org.http4s.syntax.kleisli._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.util.Properties

import java.util.concurrent.{Executors, TimeUnit}

object Main extends IOApp with HistogramStoreImplicits with LazyLogging {

  val rasterIO: ContextShift[IO] = IO.contextShift(
    ExecutionContext.fromExecutor(
      Executors.newCachedThreadPool(
        new ThreadFactoryBuilder().setNameFormat("raster-io-%d").build()
      )
    ))

  override implicit val contextShift: ContextShift[IO] = rasterIO

  val xa = RFTransactor.buildTransactor()

  val ogcUrlPrefix = Properties.envOrNone("ENVIRONMENT") match {
    case Some("Production") => "https://tiles.rasterfoundry.com"
    case Some("Staging")    => "https://tiles.staging.rasterfoundry.com"
    case _                  => "http://localhost:8081"
  }

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

  def baseMiddleware(svc: HttpRoutes[IO]) =
    RequestRewriteMiddleware(withCORS(withTimeout(svc)), xa)

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

  val projectStoreImplicits = new RenderableStoreImplicits(xa)
  import projectStoreImplicits._

  val projectLayerMosaicImplicits =
    new MosaicImplicits(LayerAttributeDao())
  val sceneMosaicImplicits =
    new MosaicImplicits(LayerAttributeDao())
  val toolStoreImplicits =
    new ToolStoreImplicits(projectLayerMosaicImplicits, xa)
  import toolStoreImplicits.toolRunDaoStore

  val ogcImplicits = new OgcImplicits(SceneToLayerDao(), xa)
  import ogcImplicits._

  val analysisManager =
    new AnalysisManager(
      ToolRunDao(),
      projectLayerMosaicImplicits,
      toolStoreImplicits,
      xa
    )

  val metricMiddleware = new MetricMiddleware(xa)
  implicit val tracingContext: TracingContextBuilder[IO] =
    if (CommonConfig.awsbatch.environment.toUpperCase == "DEVELOPMENT") {
      JaegerTracer.tracingContextBuilder
    } else {
      XRayTracer.tracingContextBuilder
    }

  val mosaicService: HttpRoutes[IO] = authenticators.tokensAuthMiddleware(
    metricMiddleware.middleware(
      new MosaicService(
        SceneToLayerDao(),
        projectLayerMosaicImplicits,
        analysisManager,
        xa,
        rasterIO
      ).routes
    )
  )

  val analysisService: HttpRoutes[IO] =
    authenticators.tokensAuthMiddleware(
      metricMiddleware.middleware(
        new AnalysisService(analysisManager).routes
      )
    )

  val sceneMosaicService: HttpRoutes[IO] =
    authenticators.tokensAuthMiddleware(
      new SceneService(sceneMosaicImplicits, xa).routes
    )

  val wcsService = authenticators.tokensAuthMiddleware(
    new WcsService(ProjectDao(), ogcUrlPrefix).routes
  )

  val wmsService = authenticators.tokensAuthMiddleware(
    new WmsService(ProjectDao(), ogcUrlPrefix).routes
  )

  def router =
    errorHandling {
      baseMiddleware {
        Router(
          "/" -> mosaicService,
          "/scenes" -> sceneMosaicService,
          "/tools" -> analysisService,
          "/wcs" -> wcsService,
          "/wms" -> wmsService,
          "/healthcheck" -> new HealthcheckService(
            xa
          ).routes
        )
      }
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
      .withBanner(startupBanner)
      .withConnectorPoolSize(Config.parallelism.blazeConnectorPoolSize)
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(router.orNotFound)
      .serve

  val canSelect = sql"SELECT 1".query[Int].unique.transact(xa).unsafeRunSync
  logger.info(s"Server Started (${canSelect})")

  def run(args: List[String]): IO[ExitCode] =
    for {
      exit <- stream.compile.drain.map(_ => ExitCode.Success)
    } yield exit
}
