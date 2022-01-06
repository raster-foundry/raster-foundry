package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash._
import com.rasterfoundry.backsplash.color.{Implicits => ColorImplicits}
import com.rasterfoundry.datamodel.User
import com.rasterfoundry.datamodel.UserWithPlatform

import cats.data.Validated._
import cats.effect._
import doobie.Transactor
import geotrellis.proj4.{LatLng, WebMercator}
import geotrellis.raster.io.geotiff._
import geotrellis.raster.render.ColorRamps
import geotrellis.raster.{io => _, _}
import geotrellis.vector.Extent
import io.chrisdavenport.log4cats.Logger
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.util.CaseInsensitiveString

import java.util.UUID

@SuppressWarnings(Array("TraversableHead"))
class AnalysisManager[Param: ToolStore, HistStore](
    analyses: Param,
    mosaicImplicits: MosaicImplicits[HistStore],
    toolstoreImplicits: ToolStoreImplicits[HistStore],
    xa: Transactor[IO]
)(implicit cs: ContextShift[IO], logger: Logger[IO])
    extends ColorImplicits {

  val authorizers = new Authorizers(xa)

  import mosaicImplicits._

  implicit val tmsReification = rawMosaicTmsReification

  import toolstoreImplicits._

  private val pngType = `Content-Type`(MediaType.image.png)
  private val tiffType = `Content-Type`(MediaType.image.tiff)

  def tile(
      user: User,
      analysisId: UUID,
      nodeId: Option[UUID],
      z: Int,
      x: Int,
      y: Int
  ): IO[Response[IO]] =
    for {
      authFiber <- authorizers.authToolRun(user, analysisId).start
      paintableFiber <- analyses.read(analysisId, nodeId).start
      _ <- authFiber.join.handleErrorWith { error =>
        paintableFiber.cancel *> IO.raiseError(error)
      }
      paintable <- paintableFiber.join
      tileValidated <- paintable.tms(z, x, y) map {
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
      resp <- tileValidated
    } yield resp

  def histogram(
      user: User,
      analysisId: UUID,
      nodeId: Option[UUID]
  ): IO[Response[IO]] =
    for {
      authFiber <- authorizers.authToolRun(user, analysisId).start
      paintableFiber <- analyses.read(analysisId, nodeId).start
      _ <- authFiber.join.handleErrorWith { error =>
        paintableFiber.cancel *> IO.raiseError(error)
      }
      paintable <- paintableFiber.join
      histsValidated <- paintable.histogram(4000) map {
        case Valid(hists) if !hists.exists(_.binCounts.isEmpty) =>
          Ok(hists.head asJson)
        case Valid(_) =>
          NotFound(s"Did not find any data for $analysisId")
        case Invalid(e) =>
          BadRequest(s"Unable to produce histogram for $analysisId: $e")
      }
      resp <- histsValidated
    } yield resp

  def statistics(
      user: User,
      analysisId: UUID,
      nodeId: Option[UUID]
  ): IO[Response[IO]] =
    for {
      authFiber <- authorizers.authToolRun(user, analysisId).start
      paintableFiber <- analyses.read(analysisId, nodeId).start
      _ <- authFiber.join.handleErrorWith { error =>
        paintableFiber.cancel *> IO.raiseError(error)
      }
      paintable <- paintableFiber.join
      histsValidated <- paintable.histogram(4000) map {
        case Valid(hists) =>
          Ok(hists.head.statistics asJson)
        case Invalid(e) =>
          BadRequest(s"Unable to produce statistics for $analysisId: $e")
      }
      resp <- histsValidated
    } yield resp

  def export(
      authedReq: AuthedRequest[IO, UserWithPlatform],
      user: User,
      analysisId: UUID,
      node: Option[UUID],
      extent: Extent,
      zoom: Int
  ) = {
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
        BacksplashImage.tmsLevels(zoom).cellSize
      ) map {
        case Valid(tile) => {
          if (respType == tiffType) {
            Ok(
              SinglebandGeoTiff(
                tile.band(0),
                projectedExtent,
                WebMercator
              ).toByteArray,
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
        case Invalid(e) =>
          BadRequest(s"Could not produce extent: $e")
      }
      resp <- tileValidated
    } yield resp
  }
}
