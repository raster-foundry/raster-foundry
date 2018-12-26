package com.rasterfoundry.backsplash.server

import com.rasterfoundry.datamodel.User
import com.rasterfoundry.backsplash._
import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.backsplash.MetricsRegistrator
import com.rasterfoundry.backsplash.Parameters._
import cats.Applicative
import cats.data.{NonEmptyList => NEL}
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
import java.util.UUID

import doobie.util.transactor.Transactor

class MosaicService[ProjStore: ProjectStore](projects: ProjStore,
                                             mtr: MetricsRegistrator,
                                             mosaicImplicits: MosaicImplicits,
                                             xa: Transactor[IO])(
    implicit cs: ContextShift[IO],
    H: HttpErrorHandler[IO, BacksplashException, User],
    ForeignError: HttpErrorHandler[IO, Throwable, User]) {

  import mosaicImplicits._

  val authorizers = new Authorizers(xa)

  val routes: AuthedService[User, IO] =
    H.handle {
      ForeignError.handle {
        AuthedService {
          case GET -> Root / UUIDWrapper(projectId) / IntVar(z) / IntVar(x) / IntVar(
                y) / _
                :? RedBandOptionalQueryParamMatcher(redOverride)
                :? GreenBandOptionalQueryParamMatcher(greenOverride)
                :? BlueBandOptionalQueryParamMatcher(blueOverride) as user =>
            val bandOverride =
              Applicative[Option].map3(redOverride,
                                       greenOverride,
                                       blueOverride)(BandOverride.apply)
            val eval =
              LayerTms.identity(
                projects.read(projectId, None, bandOverride, None))
            for {
              _ <- authorizers.authProject(user, projectId)
              resp <- eval(z, x, y) flatMap {
                case Valid(tile) =>
                  Ok(tile.renderPng.bytes, `Content-Type`(MediaType.image.png))
                case Invalid(e) =>
                  BadRequest(s"Could not produce tile: $e")
              }
            } yield resp

          case GET -> Root / UUIDWrapper(projectId) / "histogram" / _ as user =>
            for {
              _ <- authorizers.authProject(user, projectId)
              mosaic = projects.read(projectId, None, None, None)
              resp <- LayerHistogram.identity(mosaic, 4000) flatMap {
                case Valid(hists) =>
                  Ok(hists map { hist =>
                    Map(hist.binCounts: _*)
                  } asJson)
                case Invalid(e) =>
                  BadRequest(s"Histograms could not be produced: $e")
              }
            } yield resp

          case authedReq @ POST -> Root / UUIDWrapper(projectId) / "histogram" / _
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
                    _ <- authorizers.authProject(user, projectId)
                    mosaic <- IO {
                      projects.read(projectId, None, overrides, uuids.toNel)
                    }
                    histsValidated <- LayerHistogram.identity(mosaic, 4000)
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

          case authedReq @ GET -> Root / UUIDWrapper(projectId) / "export" / _
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
            // TODO: this will come from the project once we're fetching the project for authorization reasons
            val toPaint: Boolean = true
            val eval =
              if (toPaint) {
                LayerExtent.identity(
                  projects.read(projectId, None, bandOverride, None))(
                  paintedMosaicExtentReification,
                  cs)
              } else {
                LayerExtent.identity(
                  projects.read(projectId, None, bandOverride, None))(
                  rawMosaicExtentReification,
                  cs)
              }
            for {
              _ <- authorizers.authProject(user, projectId)
              resp <- eval(extent, cellSize) flatMap {
                case Valid(tile) =>
                  authedReq.req.headers
                    .get(CaseInsensitiveString("Accept")) match {
                    case Some(Header(_, "image/tiff")) =>
                      Ok(
                        MultibandGeoTiff(tile, projectedExtent, WebMercator).toByteArray,
                        `Content-Type`(MediaType.image.tiff)
                      )
                    case _ =>
                      Ok(tile.renderPng.bytes,
                         `Content-Type`(MediaType.image.png))
                  }
                case Invalid(e) => BadRequest(s"Could not produce extent: $e")
              }
            } yield resp
        }
      }
    }
}
