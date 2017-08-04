package com.azavea.rf.tile.image

import com.azavea.rf.tile._
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.Projects
import com.azavea.rf.datamodel.Project
import geotrellis.raster._
import geotrellis.raster.render.Png
import geotrellis.slick.Projected
import geotrellis.vector.Polygon
import cats.data._
import cats.implicits._
import java.util.UUID

import com.azavea.rf.database.ExtendedPostgresDriver.api._

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import com.typesafe.scalalogging.LazyLogging

case class TagWithTTL(tag: String, ttl: Duration)

object Mosaic extends LazyLogging with KamonTrace {
  implicit val database = Database.DEFAULT

  def apply(
      projectId: UUID,
      zoom: Int,
      col: Int,
      row: Int
  )(implicit database: Database)
      : OptionT[Future, MultibandTile] = traceName(s"Mosaic.apply($projectId)") {
    OptionT(Projects.getProjectPreauthorized(projectId)) flatMap { project =>
      project.isSingleBand match {
        case true =>
          SingleBandMosaic(project, zoom, col, row)
        case false =>
          MultiBandMosaic(projectId, zoom, col, row)
      }
    }
  }

  def render(
    projectId: UUID,
    zoomOption: Option[Int],
    bboxOption: Option[String],
    colorCorrect: Boolean
  )(implicit database: Database): OptionT[Future, MultibandTile] = {
    OptionT(Projects.getProjectPreauthorized(projectId)) flatMap { project =>
      project.isSingleBand match {
        case true =>
          /* TODO: handle single band mosaics*/
          SingleBandMosaic.render(project, zoomOption, bboxOption, colorCorrect)
        case false =>
          MultiBandMosaic.render(projectId, zoomOption, bboxOption, colorCorrect)
      }
    }
  }

  def raw(
    projectId: UUID,
    zoom: Int,
    col: Int,
    row: Int
  )(implicit database: Database): OptionT[Future, MultibandTile] = {
    MultiBandMosaic.raw(projectId, zoom, col, row)
  }

  def rawForExtent(
    projectId: UUID,
    zoom: Int,
    bbox: Option[Projected[Polygon]]
  )(implicit database: Database) : OptionT[Future, MultibandTile] = {
    MultiBandMosaic.rawForExtent(projectId, zoom, bbox)
  }
}
