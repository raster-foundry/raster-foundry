package com.rasterfoundry.backsplash

import cats.data.Validated._
import cats.effect._
import fs2.Stream
import geotrellis.server._
import io.circe._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.server.middleware.{AutoSlash, CORS, CORSConfig}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.Router
import org.http4s.syntax.kleisli._

import Implicits._
import Parameters._
import Store._

import java.util.UUID

object Server extends IOApp {

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

  println(s"Kansas layer: http://localhost:8080/$kansasUUID/{z}/{x}/{y}/")
  println(s"Sentinel layer: http://localhost:8080/$sentinelUUID/{z}/{x}/{y}/")

  val service: HttpRoutes[IO] = HttpRoutes.of {
    case GET -> Root / UUIDWrapper(projId) / IntVar(z) / IntVar(x) / IntVar(y) =>
      val eval = LayerTms.identity(projects.read(projId))
      eval(z, x, y) flatMap {
        case Valid(tile) =>
          Ok(tile.renderPng.bytes, `Content-Type`(MediaType.image.png))
        case _ =>
          NotFound()
      }

    case GET -> Root / UUIDWrapper(projId) / "histograms" =>
      BacksplashMosaic.layerHistogram(projects.read(projId)) flatMap {
        case Valid(hists) => Ok("sure")
        case _ => BadRequest("nah my dude")
      }

    case GET -> Root / UUIDWrapper(projectId) / "export"
        :? ExtentQueryParamMatcher(extent)
        :? ZoomQueryParamMatcher(zoom) =>
      println("in export")
      val cellSize = BacksplashImage.tmsLevels(zoom).cellSize
      println("got cell size")
      val eval = LayerExtent.identity(projects.read(projectId))
      println("build eval")
      eval(extent, cellSize) flatMap {
        case Valid(tile) => Ok(tile.renderPng.bytes, `Content-Type`(MediaType.image.png))
        case _ => BadRequest("nah my dude")
      }
  }

  val httpApp =
    Router(
      "/" -> AutoSlash(service)
    )

  def stream =
    BlazeServerBuilder[IO]
      .enableHttp2(true)
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(httpApp.orNotFound)
      .serve

  def run(args: List[String]): IO[ExitCode] =
    stream.compile.drain.map(_ => ExitCode.Success)
}
