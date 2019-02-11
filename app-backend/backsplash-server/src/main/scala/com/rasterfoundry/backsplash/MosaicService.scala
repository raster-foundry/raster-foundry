package com.rasterfoundry.backsplash.server

import com.rasterfoundry.common.datamodel.User
import com.rasterfoundry.backsplash._
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.backsplash.Parameters._
import com.rasterfoundry.common.utils.TileUtils

import cats.data.Validated._
import cats.effect.{ContextShift, IO}
import cats.implicits._
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
import doobie.util.transactor.Transactor
import geotrellis.vector.{Polygon, Projected}

import java.util.UUID

class MosaicService[LayerStore: ProjectStore, HistStore](
    layers: LayerStore,
    mosaicImplicits: MosaicImplicits[HistStore],
    xa: Transactor[IO])(implicit cs: ContextShift[IO],
                        H: HttpErrorHandler[IO, BacksplashException, User],
                        ForeignError: HttpErrorHandler[IO, Throwable, User]) {

  import mosaicImplicits._

  implicit val tmsReification = paintedMosaicTmsReification

  private val pngType = `Content-Type`(MediaType.image.png)
  private val tiffType = `Content-Type`(MediaType.image.tiff)

  val authorizers = new Authorizers(xa)

  val routes: AuthedService[User, IO] =
    H.handle {
      ForeignError.handle {
        AuthedService {
          case GET -> Root / UUIDWrapper(projectId) / "layers" / UUIDWrapper(
                layerId) / IntVar(z) / IntVar(x) / IntVar(y) :? BandOverrideQueryParamDecoder(
                bandOverride) as user =>
            val polygonBbox: Projected[Polygon] =
              TileUtils.getTileBounds(z, x, y)
            val eval = LayerTms.identity(
              layers.read(layerId, Some(polygonBbox), bandOverride, None)
            )

            for {
              fiberAuthProject <- authorizers.authProject(user, projectId).start
              fiberAuthLayer <- authorizers
                .authProjectLayer(projectId, layerId)
                .start
              fiberResp <- eval(z, x, y).start
              _ <- (fiberAuthProject, fiberAuthLayer).tupled.join
                .handleErrorWith { error =>
                  fiberResp.cancel *> IO.raiseError(error)
                }
              resp <- fiberResp.join flatMap {
                case Valid(tile) =>
                  Ok(tile.renderPng.bytes, pngType)
                case Invalid(e) =>
                  BadRequest(s"Could not produce tile: $e")
              }
            } yield resp

          case GET -> Root / UUIDWrapper(projectId) / "layers" / UUIDWrapper(
                layerId) / "histogram" :? BandOverrideQueryParamDecoder(
                overrides) as user =>
            for {
              authFiber <- authorizers.authProject(user, projectId).start
              mosaic = layers.read(layerId, None, overrides, None)
              histFiber <- LayerHistogram.identity(mosaic, 4000).start
              _ <- authFiber.join.handleErrorWith { error =>
                histFiber.cancel *> IO.raiseError(error)
              }
              resp <- histFiber.join.flatMap {
                case Valid(hists) =>
                  Ok(hists map { hist =>
                    Map(hist.binCounts: _*)
                  } asJson)
                case Invalid(e) =>
                  BadRequest(s"Histograms could not be produced: $e")
              }
            } yield resp

          case authedReq @ POST -> Root / UUIDWrapper(projectId) / "layers" / UUIDWrapper(
                layerId) / "histogram"
                :? BandOverrideQueryParamDecoder(overrides) as user =>
            // Compile to a byte array, decode that as a string, and do something with the results
            authedReq.req.body.compile.to[Array] flatMap { uuids =>
              decode[List[UUID]](
                uuids map { _.toChar } mkString
              ) match {
                case Right(uuids) =>
                  for {
                    authFiber <- authorizers.authProject(user, projectId).start
                    mosaic = layers.read(layerId, None, overrides, uuids.toNel)
                    histFiber <- LayerHistogram.identity(mosaic, 4000).start
                    _ <- authFiber.join.handleErrorWith { error =>
                      histFiber.cancel *> IO.raiseError(error)
                    }
                    resp <- histFiber.join.flatMap {
                      case Valid(hists) => Ok(hists asJson)
                      case Invalid(e) =>
                        BadRequest(s"Unable to produce histograms: $e")
                    }
                  } yield resp
                case _ =>
                  BadRequest(
                    """
                    | Could not decode body as sequence of UUIDs.
                    | Format should be, e.g.,
                    | ["342a82e2-a5c1-4b35-bb6b-0b9dd6a52fa7", "8f3bb3fc-4bd6-49e8-9b25-6fa075cc0c77
"]""".trim.stripMargin)
              }
            }

          case authedReq @ GET -> Root / UUIDWrapper(projectId) / "layers" / UUIDWrapper(
                layerId) / "export"
                :? ExtentQueryParamMatcher(extent)
                :? ZoomQueryParamMatcher(zoom)
                :? BandOverrideQueryParamDecoder(bandOverride) as user =>
            val projectedExtent = extent.reproject(LatLng, WebMercator)
            val cellSize = BacksplashImage.tmsLevels(zoom).cellSize
            val eval = authedReq.req.headers
              .get(CaseInsensitiveString("Accept")) match {
              case Some(Header(_, "image/tiff")) =>
                LayerExtent.identity(
                  layers.read(layerId,
                              Some(Projected(projectedExtent, 3857)),
                              bandOverride,
                              None))(rawMosaicExtentReification, cs)
              case _ =>
                LayerExtent.identity(
                  layers.read(layerId,
                              Some(Projected(projectedExtent, 3857)),
                              bandOverride,
                              None))(paintedMosaicExtentReification, cs)
            }
            for {
              authFiber <- authorizers.authProject(user, projectId).start
              respFiber <- eval(projectedExtent, cellSize).start
              _ <- authFiber.join.handleErrorWith { error =>
                respFiber.cancel *> IO.raiseError(error)
              }
              resp <- respFiber.join.flatMap {
                case Valid(tile) =>
                  authedReq.req.headers
                    .get(CaseInsensitiveString("Accept")) match {
                    case Some(Header(_, "image/tiff")) =>
                      Ok(
                        MultibandGeoTiff(tile, projectedExtent, WebMercator).toByteArray,
                        tiffType
                      )
                    case _ =>
                      Ok(tile.renderPng.bytes, pngType)
                  }
                case Invalid(e) => BadRequest(s"Could not produce extent: $e")
              }
            } yield resp
        }
      }
    }
}
