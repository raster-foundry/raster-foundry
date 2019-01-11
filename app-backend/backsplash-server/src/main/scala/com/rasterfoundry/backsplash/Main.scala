package com.rasterfoundry.backsplash.server

import com.rasterfoundry.database.{
  LayerAttributeDao,
  SceneDao,
  SceneToProjectDao,
  ToolRunDao
}
import com.rasterfoundry.datamodel.User
import cats.Applicative
import cats.data.OptionT
import cats.data.Validated._
import cats.effect._
import cats.implicits._
import com.olegpy.meow.hierarchy._
import fs2.Stream
import geotrellis.gdal.sgdal
import geotrellis.proj4.{LatLng, WebMercator}
import geotrellis.raster.io.geotiff.MultibandGeoTiff
import geotrellis.server._
import io.circe._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.server.middleware.{AutoSlash, CORS, CORSConfig, Timeout}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.Router
import org.http4s.syntax.kleisli._
import org.http4s.util.CaseInsensitiveString

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.{Executors, TimeUnit}

import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.backsplash.MosaicImplicits
import com.rasterfoundry.backsplash.Parameters._
import com.rasterfoundry.backsplash.MetricsRegistrator
import java.util.UUID

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.rasterfoundry.database.util.RFTransactor
import com.typesafe.scalalogging.LazyLogging
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._

object Main extends IOApp with HistogramStoreImplicits with LazyLogging {

  val dbContextShift: ContextShift[IO] = IO.contextShift(
    ExecutionContext.fromExecutor(
      Executors.newFixedThreadPool(
        Config.parallelism.dbThreadPoolSize,
        new ThreadFactoryBuilder().setNameFormat("db-client-%d").build())
    ))

  val xa = RFTransactor.transactor(dbContextShift)

  override protected implicit def contextShift: ContextShift[IO] =
    IO.contextShift(
      ExecutionContext.fromExecutor(
        Executors.newFixedThreadPool(
          Config.parallelism.http4sThreadPoolSize,
          new ThreadFactoryBuilder().setNameFormat("http4s-%d").build()
        )
      ))

  val blazeEC = ExecutionContext.fromExecutor(
    Executors.newFixedThreadPool(
      Config.parallelism.blazeThreadPoolSize,
      new ThreadFactoryBuilder().setNameFormat("blaze-cached-%d").build())
  )

  val mtr = new MetricsRegistrator()

  val projectStoreImplicits = new ProjectStoreImplicits(xa, mtr)
  import projectStoreImplicits.projectStore

  val timeout: FiniteDuration =
    new FiniteDuration(Config.server.timeoutSeconds, TimeUnit.SECONDS)

  implicit val backsplashErrorHandler
    : HttpErrorHandler[IO, BacksplashException, User] =
    new BacksplashHttpErrorHandler[IO, User]

  implicit val foreignErrorHandler: HttpErrorHandler[IO, Throwable, User] =
    new ForeignErrorHandler[IO, Throwable, User]

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

  val authenticators = new Authenticators(xa, mtr)

  val mosaicImplicits = new MosaicImplicits(mtr, LayerAttributeDao())
  val toolStoreImplicits = new ToolStoreImplicits(mosaicImplicits, xa, mtr)
  import toolStoreImplicits._

  val mosaicService: HttpRoutes[IO] =
    authenticators.tokensAuthMiddleware(AuthedAutoSlash(
      new MosaicService(SceneToProjectDao(), mtr, mosaicImplicits, xa).routes))

  val analysisService: HttpRoutes[IO] =
    authenticators.tokensAuthMiddleware(
      AuthedAutoSlash(
        new AnalysisService(ToolRunDao(),
                            mtr,
                            mosaicImplicits,
                            toolStoreImplicits,
                            xa).routes))

  val sceneMosaicService: HttpRoutes[IO] =
    authenticators.tokensAuthMiddleware(
      AuthedAutoSlash(
        new SceneService(SceneDao(),
                         mtr,
                         mosaicImplicits,
                         LayerAttributeDao(),
                         xa).routes
      )
    )

  val httpApp =
    Router(
      "/" -> mtr.middleware(withCORS(withTimeout(mosaicService))),
      "/scenes" -> mtr.middleware(withCORS(withTimeout(sceneMosaicService))),
      "/tools" -> mtr.middleware(withCORS(withTimeout(analysisService))),
      "/healthcheck" -> AutoSlash(new HealthcheckService[IO]().routes)
    )

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
      _ <- IO { mtr.reportToGraphite(Config.server.graphiteUrl) }
      exit <- stream.compile.drain.map(_ => ExitCode.Success)
    } yield exit
}
