package com.rasterfoundry.backsplash

import cats.data.Validated._
import cats.effect._
import fs2.Stream
import geotrellis.server._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.server.middleware.{AutoSlash, CORS, CORSConfig}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.Router
import org.http4s.syntax.kleisli._

import Implicits._
import Parameters._

import java.util.UUID

object Server extends IOApp {
  val aUUID = UUID.randomUUID
  println(s"Here's a layer: http://localhost:8080/$aUUID/{z}/{x}/{y}/")
  val projects: Map[UUID, BacksplashMosaic] = Map(
    aUUID -> (Stream.eval {
      IO.pure {
        BacksplashImage(
          "s3://radiant-nasa-iserv/2014/09/20/IPR201409201521563491N09706W/IPR201409201521563491N09706W-COG.tif",
          "POLYGON ((-97.1725628915165345 34.8130631368108112, -96.9454161960794210 34.8130631368108112, -96.9454161960794210 35.0064019519968568, -97.1725628915165345 35.0064019519968568, -97.1725628915165345 34.8130631368108112))",
          List(2, 1, 0))
      }
    }).repeat.take(100)
  )
  implicit val projStore: ProjectStore[Map[UUID, BacksplashMosaic]] = new ProjectStore[Map[UUID, BacksplashMosaic]] {
    def read(self: Map[UUID, BacksplashMosaic], projId: UUID) = self.get(projId).get
  }

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

  val service: HttpRoutes[IO] = HttpRoutes.of {
    case GET -> Root / UUIDWrapper(projId) / IntVar(z) / IntVar(x) / IntVar(y) =>
      val eval = LayerTms.identity(projects.read(projId))
      eval(z, x, y) flatMap {
        case Valid(tile) =>
          Ok(tile.renderPng.bytes, `Content-Type`(MediaType.image.png))
        case _ =>
          BadRequest("you suuuuuuuuuck")
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
