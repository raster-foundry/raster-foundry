package com.rasterfoundry.backsplash.server

import com.rasterfoundry.database.{SceneToProjectDao, ToolRunDao}
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
import org.http4s.server.middleware.{AutoSlash, CORS, CORSConfig, GZip, Timeout}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.Router
import org.http4s.syntax.kleisli._
import org.http4s.util.CaseInsensitiveString
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.{TimeUnit, Executors}

import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.backsplash.MosaicImplicits
import com.rasterfoundry.backsplash.Parameters._
import com.rasterfoundry.backsplash.MetricsRegistrator

import java.util.UUID

object Main extends IOApp with ProjectStoreImplicits {

  sgdal.setConfigOption("GDAL_DISABLE_READDIR_ON_OPEN", "YES")
  sgdal.setConfigOption("GDAL_MAX_DATASET_POOL_SIZE", "1")
  sgdal.setConfigOption("VRT_SHARED_SOURCE", "0")

  override protected implicit def contextShift: ContextShift[IO] =
    IO.contextShift(
      ExecutionContext.fromExecutor(
        Executors.newFixedThreadPool(
          Config.parallelism.threadPoolSize
        )))

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

  val mtr = new MetricsRegistrator()

  val mosaicImplicits = new MosaicImplicits(mtr)
  val toolStoreImplicits = new ToolStoreImplicits(mosaicImplicits)
  import toolStoreImplicits._

  val mosaicService: HttpRoutes[IO] =
    Authenticators.tokensAuthMiddleware(
      new MosaicService(SceneToProjectDao(), mtr, mosaicImplicits).routes)

  val analysisService: HttpRoutes[IO] =
    Authenticators.tokensAuthMiddleware(
      new AnalysisService(ToolRunDao(),
                          mtr,
                          mosaicImplicits,
                          toolStoreImplicits).routes)

  val httpApp =
    Router(
      "/" -> mtr.middleware(
        GZip(AutoSlash(withCORS(withTimeout(mosaicService))))),
      "/tools" -> mtr.middleware(
        GZip(AutoSlash(withCORS(withTimeout(analysisService))))),
      "/healthcheck" -> AutoSlash(new HealthcheckService[IO]().routes)
    )

  def stream =
    BlazeServerBuilder[IO]
      .enableHttp2(true)
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(httpApp.orNotFound)
      .serve

  def run(args: List[String]): IO[ExitCode] =
    for {
      _ <- IO { mtr.reportToGraphite(Config.server.graphiteUrl) }
      exit <- stream.compile.drain.map(_ => ExitCode.Success)
    } yield exit
}
