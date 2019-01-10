package com.rasterfoundry.backsplash.server

import com.rasterfoundry.datamodel.User
import com.rasterfoundry.backsplash._
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.backsplash.MetricsRegistrator
import com.rasterfoundry.backsplash.Parameters._
import cats.Applicative
import cats.data.{NonEmptyList => NEL}
import cats.data.Validated._
import cats.effect.{ContextShift, Fiber, IO}
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
import java.util.UUID

import com.rasterfoundry.common.utils.TileUtils
import doobie.util.transactor.Transactor
import geotrellis.vector.{Polygon, Projected}

class MosaicService[ProjStore: ProjectStore, HistStore: HistogramStore](
    projects: ProjStore,
    mtr: MetricsRegistrator,
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
          case GET -> Root / UUIDWrapper(projectId) / IntVar(z) / IntVar(x) / IntVar(
                y)
                :? RedBandOptionalQueryParamMatcher(redOverride)
                :? GreenBandOptionalQueryParamMatcher(greenOverride)
                :? BlueBandOptionalQueryParamMatcher(blueOverride) as user =>
            val bandOverride =
              Applicative[Option].map3(redOverride,
                                       greenOverride,
                                       blueOverride)(BandOverride.apply)
            val polygonBbox: Projected[Polygon] =
              TileUtils.getTileBounds(z, x, y)
            val eval =
              LayerTms.identity(
                projects.read(projectId, Some(polygonBbox), bandOverride, None))
            for {
              fiberAuth <- authorizers.authProject(user, projectId).start
              fiberResp <- eval(z, x, y).start
              _ <- fiberAuth.join.handleErrorWith { error =>
                fiberResp.cancel *> IO.raiseError(error)
              }
              resp <- fiberResp.join flatMap {
                case Valid(tile) =>
                  Ok(tile.renderPng.bytes, pngType)
                case Invalid(e) =>
                  BadRequest(s"Could not produce tile: $e")
              }
            } yield resp

          case GET -> Root / UUIDWrapper(projectId) / "histogram" as user =>
            for {
              authFiber <- authorizers.authProject(user, projectId).start
              mosaic = projects.read(projectId, None, None, None)
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

          case authedReq @ POST -> Root / UUIDWrapper(projectId) / "histogram"
                :? RedBandOptionalQueryParamMatcher(redOverride)
                :? GreenBandOptionalQueryParamMatcher(greenOverride)
                :? BlueBandOptionalQueryParamMatcher(blueOverride) as user =>
            // Compile to a byte array, decode that as a string, and do something with the results
            authedReq.req.body.compile.to[Array] flatMap { uuids =>
              decode[List[UUID]](
                uuids map { _.toChar } mkString
              ) match {
                case Right(uuids) =>
                  val overrides =
                    (redOverride, greenOverride, blueOverride).mapN {
                      case (r, g, b) => BandOverride(r, g, b)
                    }
                  for {
                    authFiber <- authorizers.authProject(user, projectId).start
                    mosaic = projects.read(projectId,
                                           None,
                                           overrides,
                                           uuids.toNel)
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
                  BadRequest("Could not decode body as sequence of UUIDs")
              }
            }

          case authedReq @ GET -> Root / UUIDWrapper(projectId) / "export"
                :? ExtentQueryParamMatcher(extent)
                :? ZoomQueryParamMatcher(zoom)
                :? RedBandOptionalQueryParamMatcher(redOverride)
                :? GreenBandOptionalQueryParamMatcher(greenOverride)
                :? BlueBandOptionalQueryParamMatcher(blueOverride) as user =>
            val bandOverride =
              Applicative[Option].map3(redOverride,
                                       greenOverride,
                                       blueOverride)(BandOverride.apply)
            val projectedExtent = extent.reproject(LatLng, WebMercator)
            val cellSize = BacksplashImage.tmsLevels(zoom).cellSize
            val eval = authedReq.req.headers
              .get(CaseInsensitiveString("Accept")) match {
              case Some(Header(_, "image/tiff")) =>
                LayerExtent.identity(
                  projects.read(projectId,
                                Some(Projected(projectedExtent, 3857)),
                                bandOverride,
                                None))(rawMosaicExtentReification, cs)
              case _ =>
                LayerExtent.identity(
                  projects.read(projectId,
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
