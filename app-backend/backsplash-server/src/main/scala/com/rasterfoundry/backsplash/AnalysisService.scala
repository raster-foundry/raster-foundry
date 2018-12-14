package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.Parameters._

import cats.data.Validated._
import cats.effect.{ContextShift, IO}
import geotrellis.proj4.{LatLng, WebMercator}
import geotrellis.raster.render.ColorRamps
import geotrellis.raster.io.geotiff._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.circe._
import org.http4s.util.CaseInsensitiveString

import com.rasterfoundry.backsplash._
import com.rasterfoundry.backsplash.Implicits._

@SuppressWarnings(Array("TraversableHead"))
class AnalysisService[Param: ToolStore](analyses: Param)(
    implicit cs: ContextShift[IO])
    extends BacksplashMamlAdapter {
  val routes: HttpRoutes[IO] = HttpRoutes.of {

    case GET -> Root / UUIDWrapper(analysisId) / "histogram"
          :? NodeQueryParamMatcher(node)
          :? VoidCacheQueryParamMatcher(void) =>
      for {
        paintable <- analyses.read(analysisId, node)
        histsValidated <- paintable.histogram(4000)
        resp <- histsValidated match {
          case Valid(hists) =>
            Ok(hists.head asJson)
          case Invalid(e) =>
            BadRequest(s"Unable to produce histogram for $analysisId: $e")
        }
      } yield resp

    case GET -> Root / UUIDWrapper(analysisId) / IntVar(z) / IntVar(x) / IntVar(
          y)
          :? NodeQueryParamMatcher(node) =>
      for {
        paintable <- analyses.read(analysisId, node)
        tileValidated <- paintable.tms(z, x, y)
        resp <- tileValidated match {
          case Valid(tile) =>
            Ok(tile.band(0).renderPng(ColorRamps.Viridis).bytes,
               `Content-Type`(MediaType.image.png))
          case Invalid(e) =>
            BadRequest(s"Unable to produce tile for $analysisId: $e")
        }
      } yield resp

    case req @ GET -> Root / UUIDWrapper(analysisId) / "raw"
          :? ExtentQueryParamMatcher(extent)
          :? ZoomQueryParamMatcher(zoom)
          :? NodeQueryParamMatcher(node) =>
      val projectedExtent = extent.reproject(LatLng, WebMercator)
      val respType =
        req.headers
          .get(CaseInsensitiveString("Accept")) match {
          case Some(Header(_, "image/tiff")) =>
            `Content-Type`(MediaType.image.tiff)
          case _ => `Content-Type`(MediaType.image.png)
        }
      val pngType = `Content-Type`(MediaType.image.png)
      val tiffType = `Content-Type`(MediaType.image.tiff)
      for {
        paintableTool <- analyses.read(analysisId, node)
        tileValidated <- paintableTool.extent(
          projectedExtent,
          BacksplashImage.tmsLevels(zoom).cellSize)
        // TODO: restore render def coloring
        resp <- tileValidated match {
          case Valid(tile) =>
            if (respType == tiffType) {
              Ok(
                SinglebandGeoTiff(tile.band(0), projectedExtent, WebMercator).toByteArray,
                tiffType)
            } else {
              Ok(tile.band(0).renderPng(ColorRamps.Viridis).bytes,
                 `Content-Type`(MediaType.image.png))
            }
          case Invalid(e) => BadRequest(s"Could not produce extent: $e")
        }
      } yield resp
  }
}
