package com.rasterfoundry.backsplash

import cats.Applicative
import cats.data.Validated._
import cats.effect._
import cats.implicits._
import fs2.Stream
import geotrellis.proj4.{LatLng, WebMercator}
import geotrellis.raster.io.geotiff.MultibandGeoTiff
import geotrellis.server._
import io.circe._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.server.middleware.{AutoSlash, CORS, CORSConfig}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.Router
import org.http4s.syntax.kleisli._
import org.http4s.util.CaseInsensitiveString

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

  println(s"Oklahoma layer: http://localhost:8080/$oklahomaUUID/{z}/{x}/{y}/")
  println(s"Sentinel layer: http://localhost:8080/$sentinelUUID/{z}/{x}/{y}/")

  val service: HttpRoutes[IO] = HttpRoutes.of {
    case GET -> Root / UUIDWrapper(projId) / IntVar(z) / IntVar(x) / IntVar(y)
          :? RedBandOptionalQueryParamMatcher(redOverride)
          :? GreenBandOptionalQueryParamMatcher(greenOverride)
          :? BlueBandOptionalQueryParamMatcher(blueOverride) =>
      val bandOverride =
        Applicative[Option].map3(redOverride, greenOverride, blueOverride)(
          BandOverride.apply)
      val eval =
        LayerTms.identity(projects.read(projId, None, bandOverride, None))
      eval(z, x, y) flatMap {
        case Valid(tile) =>
          Ok(tile.renderPng.bytes, `Content-Type`(MediaType.image.png))
        case _ =>
          NotFound()
      }

    case GET -> Root / UUIDWrapper(projId) / "histograms" =>
      BacksplashMosaic.layerHistogram(projects.read(projId, None, None, None)) flatMap {
        case Valid(hists) =>
          Ok(hists map { hist =>
            Map(hist.binCounts: _*)
          } asJson)
        case _ => BadRequest("nah my dude")
      }

    case req @ GET -> Root / UUIDWrapper(projectId) / "export"
          :? ExtentQueryParamMatcher(extent)
          :? ZoomQueryParamMatcher(zoom)
          :? RedBandOptionalQueryParamMatcher(redOverride)
          :? GreenBandOptionalQueryParamMatcher(greenOverride)
          :? BlueBandOptionalQueryParamMatcher(blueOverride) =>
      val bandOverride =
        Applicative[Option].map3(redOverride, greenOverride, blueOverride)(
          BandOverride.apply)
      val projectedExtent = extent.reproject(LatLng, WebMercator)
      val cellSize = BacksplashImage.tmsLevels(zoom).cellSize
      val eval =
        LayerExtent.identity(projects.read(projectId, None, bandOverride, None))
      eval(extent, cellSize) flatMap {
        case Valid(tile) =>
          req.headers
            .get(CaseInsensitiveString("Accept")) match {
            case Some(Header(_, "image/tiff")) =>
              Ok(
                MultibandGeoTiff(tile, projectedExtent, WebMercator).toByteArray,
                `Content-Type`(MediaType.image.tiff)
              )
            case _ =>
              Ok(tile.renderPng.bytes, `Content-Type`(MediaType.image.png))
          }
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
