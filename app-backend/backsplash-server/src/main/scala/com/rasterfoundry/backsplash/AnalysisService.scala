package com.rasterfoundry.backsplash.server

import com.rasterfoundry.datamodel.User

import cats.data.Validated._
import cats.effect.{ContextShift, IO}
import geotrellis.proj4.{LatLng, WebMercator}
import geotrellis.raster.{io => _, _}
import geotrellis.raster.render.ColorRamps
import geotrellis.raster.io.geotiff._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.circe._
import org.http4s.util.CaseInsensitiveString

import com.rasterfoundry.backsplash._
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.backsplash.MetricsRegistrator
import com.rasterfoundry.backsplash.Parameters._
import com.rasterfoundry.backsplash.color.{Implicits => ColorImplicits}

@SuppressWarnings(Array("TraversableHead"))
class AnalysisService[Param: ToolStore](analyses: Param,
                                        mtr: MetricsRegistrator,
                                        mosaicImplicits: MosaicImplicits,
                                        toolstoreImplicits: ToolStoreImplicits)(
    implicit cs: ContextShift[IO],
    H: HttpErrorHandler[IO, BacksplashException, User],
    ForeignError: HttpErrorHandler[IO, Throwable, User])
    extends ColorImplicits {
  import mosaicImplicits._
  import toolstoreImplicits._

  val routes: AuthedService[User, IO] = H.handle {
    ForeignError.handle {
      AuthedService {

        case GET -> Root / UUIDWrapper(analysisId) / "histogram" / _
              :? NodeQueryParamMatcher(node)
              :? VoidCacheQueryParamMatcher(void) as user =>
          for {
            authorized <- Authorizers.authToolRun(user, analysisId)
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
              y) / _
              :? NodeQueryParamMatcher(node) as user =>
          for {
            authorized <- Authorizers.authToolRun(user, analysisId)
            paintable <- analyses.read(analysisId, node)
            tileValidated <- paintable.tms(z, x, y)
            resp <- tileValidated match {
              case Valid(tile) =>
                Ok(
                  paintable.renderDefinition map { renderDef =>
                    tile.band(0).renderPng(renderDef).bytes
                  } getOrElse {
                    tile.band(0).renderPng(ColorRamps.Viridis).bytes
                  }
                )
              case Invalid(e) =>
                BadRequest(s"Unable to produce tile for $analysisId: $e")
            }
          } yield resp

        case authedReq @ GET -> Root / UUIDWrapper(analysisId) / "raw" / _
              :? ExtentQueryParamMatcher(extent)
              :? ZoomQueryParamMatcher(zoom)
              :? NodeQueryParamMatcher(node) as user =>
          val projectedExtent = extent.reproject(LatLng, WebMercator)
          val respType =
            authedReq.req.headers
              .get(CaseInsensitiveString("Accept")) match {
              case Some(Header(_, "image/tiff")) =>
                `Content-Type`(MediaType.image.tiff)
              case _ => `Content-Type`(MediaType.image.png)
            }
          val pngType = `Content-Type`(MediaType.image.png)
          val tiffType = `Content-Type`(MediaType.image.tiff)
          for {
            authorized <- Authorizers.authToolRun(user, analysisId)
            paintableTool <- analyses.read(analysisId, node)
            tileValidated <- paintableTool.extent(
              projectedExtent,
              BacksplashImage.tmsLevels(zoom).cellSize)
            // TODO: restore render def coloring
            resp <- tileValidated match {
              case Valid(tile) =>
                if (respType == tiffType) {
                  Ok(SinglebandGeoTiff(tile.band(0),
                                       projectedExtent,
                                       WebMercator).toByteArray,
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
  }

}
