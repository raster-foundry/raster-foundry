package com.rasterfoundry.backsplash.server

import cats.Applicative
import cats.data.{NonEmptyList => NEL}
import cats.data.Validated._
import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.rasterfoundry.backsplash.Parameters._
import geotrellis.proj4.{LatLng, WebMercator}
import geotrellis.raster.io.geotiff.MultibandGeoTiff
import geotrellis.server._
import io.circe.parser._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.util.CaseInsensitiveString

import com.rasterfoundry.backsplash._
import com.rasterfoundry.backsplash.Implicits._

import java.util.UUID

class MosaicService[Param: ProjectStore](projects: Param)(
    implicit cs: ContextShift[IO]) {
  val routes: HttpRoutes[IO] = HttpRoutes.of {
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
        case Invalid(e) =>
          BadRequest(s"Could not produce tile: $e")
      }

    case GET -> Root / UUIDWrapper(projId) / "histogram" =>
      BacksplashMosaic.layerHistogram(projects.read(projId, None, None, None)) flatMap {
        case Valid(hists) =>
          Ok(hists map { hist =>
            Map(hist.binCounts: _*)
          } asJson)
        case Invalid(e) => BadRequest(s"Histograms could not be produced: $e")
      }

    case req @ POST -> Root / UUIDWrapper(projectId) / "histogram" / _
          :? RedBandOptionalQueryParamMatcher(redOverride)
          :? GreenBandOptionalQueryParamMatcher(greenOverride)
          :? BlueBandOptionalQueryParamMatcher(blueOverride) =>
      // Compile to a byte array, decode that as a string, and do something with the results
      req.body.compile.to[Array] flatMap { uuids =>
        decode[List[UUID]](
          uuids map { _.toChar } mkString
        ) match {
          case Right(uuids) =>
            for {
              histsValidated <- BacksplashMosaic.layerHistogram(
                projects.read(
                  projectId,
                  None,
                  Applicative[Option].map3(redOverride,
                                           greenOverride,
                                           blueOverride)(BandOverride.apply),
                  uuids.toNel)
              )
              resp <- histsValidated match {
                case Valid(hists) => Ok(hists asJson)
                case Invalid(e) =>
                  BadRequest(s"Unable to produce histograms: $e")
              }
            } yield resp
          case _ =>
            BadRequest("Could not decode body as sequence of UUIDs")
        }
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
        case Invalid(e) => BadRequest(s"Could not produce extent: $e")
      }
  }
}
