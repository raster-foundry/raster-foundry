package com.rasterfoundry.backsplash.server

import com.rasterfoundry.datamodel.User
import cats.data.Validated._
import cats.effect.{ContextShift, IO, Fiber}
import cats.implicits._
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
import doobie.util.transactor.Transactor

@SuppressWarnings(Array("TraversableHead"))
class AnalysisService[Param: ToolStore, HistStore: HistogramStore](
    analyses: Param,
    mtr: MetricsRegistrator,
    mosaicImplicits: MosaicImplicits[HistStore],
    toolstoreImplicits: ToolStoreImplicits[HistStore],
    xa: Transactor[IO])(implicit cs: ContextShift[IO],
                        H: HttpErrorHandler[IO, BacksplashException, User],
                        ForeignError: HttpErrorHandler[IO, Throwable, User])
    extends ColorImplicits {

  import mosaicImplicits._

  implicit val tmsReification = rawMosaicTmsReification

  import toolstoreImplicits._

  private val pngType = `Content-Type`(MediaType.image.png)
  private val tiffType = `Content-Type`(MediaType.image.tiff)

  val authorizers = new Authorizers(xa)
  val routes: AuthedService[User, IO] = H.handle {
    ForeignError.handle {
      AuthedService {
        case GET -> Root / UUIDWrapper(analysisId) / "histogram"
              :? NodeQueryParamMatcher(node)
              :? VoidCacheQueryParamMatcher(void) as user =>
          for {
            authFiber <- authorizers.authToolRun(user, analysisId).start
            paintableFiber <- analyses.read(analysisId, node).start
            _ <- authFiber.join.handleErrorWith { error =>
              paintableFiber.cancel *> IO.raiseError(error)
            }
            paintable <- paintableFiber.join
            histsValidated <- paintable.histogram(4000)
            resp <- histsValidated match {
              case Valid(hists) =>
                Ok(hists.head asJson)
              case Invalid(e) =>
                BadRequest(s"Unable to produce histogram for $analysisId: $e")
            }
          } yield resp

        case GET -> Root / UUIDWrapper(analysisId) / "statistics"
              :? NodeQueryParamMatcher(node)
              :? VoidCacheQueryParamMatcher(void) as user =>
          for {
            authFiber <- authorizers.authToolRun(user, analysisId).start
            paintableFiber <- analyses.read(analysisId, node).start
            _ <- authFiber.join.handleErrorWith { error =>
              paintableFiber.cancel *> IO.raiseError(error)
            }
            paintable <- paintableFiber.join
            histsValidated <- paintable.histogram(4000)
            resp <- histsValidated match {
              case Valid(hists) =>
                Ok(hists.head.statistics asJson)
              case Invalid(e) =>
                BadRequest(s"Unable to produce statistics for $analysisId: $e")
            }
          } yield resp

        case GET -> Root / UUIDWrapper(analysisId) / IntVar(z) / IntVar(x) / IntVar(
              y)
              :? NodeQueryParamMatcher(node) as user =>
          for {
            authFiber <- authorizers.authToolRun(user, analysisId).start
            paintableFiber <- analyses.read(analysisId, node).start
            _ <- authFiber.join.handleErrorWith { error =>
              paintableFiber.cancel *> IO.raiseError(error)
            }
            paintable <- paintableFiber.join
            tileValidated <- paintable.tms(z, x, y)
            resp <- tileValidated match {
              case Valid(tile) =>
                Ok(
                  paintable.renderDefinition map { renderDef =>
                    tile.band(0).renderPng(renderDef).bytes
                  } getOrElse {
                    tile.band(0).renderPng(ColorRamps.Viridis).bytes
                  },
                  pngType
                )
              case Invalid(e) =>
                BadRequest(s"Unable to produce tile for $analysisId: $e")
            }
          } yield resp

        case authedReq @ GET -> Root / UUIDWrapper(analysisId) / "raw"
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
          for {
            authFiber <- authorizers.authToolRun(user, analysisId).start
            paintableFiber <- analyses.read(analysisId, node).start
            _ <- authFiber.join.handleErrorWith { error =>
              paintableFiber.cancel *> IO.raiseError(error)
            }
            paintableTool <- paintableFiber.join
            tileValidated <- paintableTool.extent(
              projectedExtent,
              BacksplashImage.tmsLevels(zoom).cellSize)
            resp <- tileValidated match {
              case Valid(tile) => {
                if (respType == tiffType) {
                  Ok(
                    SinglebandGeoTiff(tile.band(0),
                                      projectedExtent,
                                      WebMercator).toByteArray,
                    tiffType
                  )
                } else {
                  val rendered = paintableTool.renderDefinition match {
                    case Some(renderDef) =>
                      tile.band(0).renderPng(renderDef)
                    case _ =>
                      tile.band(0).renderPng(ColorRamps.Viridis)
                  }
                  Ok(rendered.bytes, pngType)
                }
              }
              case Invalid(e) => BadRequest(s"Could not produce extent: $e")
            }
          } yield resp
      }
    }
  }

}
