package com.azavea.rf.tile.image

import com.azavea.rf.tile._
import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.database.{ProjectDao}
import com.azavea.rf.datamodel.Project
import com.azavea.rf.database.util.RFTransactor
import com.azavea.rf.common.cache.CacheClient
import com.azavea.rf.common.cache.kryo.KryoMemcachedClient

import geotrellis.raster._
import geotrellis.raster.render.Png
import geotrellis.slick.Projected
import geotrellis.proj4._
import geotrellis.vector.{Extent, Polygon}
import cats.data._
import cats.implicits._
import cats.effect.IO
import java.util.UUID

import doobie.util.transactor.Transactor
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import com.typesafe.scalalogging.LazyLogging

case class TagWithTTL(tag: String, ttl: Duration)

object Mosaic extends LazyLogging with KamonTrace {
  implicit lazy val xa = RFTransactor.xa

  lazy val memcachedClient = KryoMemcachedClient.DEFAULT
  val rfCache = new CacheClient(memcachedClient)

  def apply(
      projectId: UUID,
      zoom: Int,
      col: Int,
      row: Int
  )(implicit xa: Transactor[IO]): OptionT[Future, MultibandTile] = traceName(s"Mosaic.apply($projectId)") {
    OptionT(ProjectDao.query.filter(projectId).selectOption.transact(xa).unsafeToFuture) flatMap { project =>
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
  )(implicit xa: Transactor[IO]): OptionT[Future, MultibandTile] = {
    OptionT(ProjectDao.query.filter(projectId).selectOption.transact(xa).unsafeToFuture) flatMap { project =>
      if (colorCorrect) {
        if (project.isSingleBand) {
            SingleBandMosaic.render(project, zoomOption, bboxOption, true)
        } else {
            MultiBandMosaic.render(projectId, zoomOption, bboxOption, true)
        }
      } else {
        val bboxPolygon: Option[Projected[Polygon]] =
          try {
            bboxOption map { bbox =>
              Projected(Extent.fromString(bbox).toPolygon(), 4326)
                .reproject(LatLng, WebMercator)(3857)
            }
          } catch {
            case e: Exception =>
              throw new IllegalArgumentException(
                "Four comma separated coordinates must be given for bbox")
                .initCause(e)
          }
        rawForExtent(projectId, zoomOption.getOrElse(8), bboxPolygon)
      }
    }
  }

  def raw(
    projectId: UUID,
    zoom: Int,
    col: Int,
    row: Int
  )(implicit xa: Transactor[IO]): OptionT[Future, MultibandTile] = {
    rfCache.cachingOptionT(s"mosaic-raw-$projectId-$zoom-$col-$row") {
      MultiBandMosaic.raw(projectId, zoom, col, row)
    }
  }

  def rawForExtent(
    projectId: UUID,
    zoom: Int,
    bbox: Option[Projected[Polygon]]
  )(implicit xa: Transactor[IO]) : OptionT[Future, MultibandTile] = {
    bbox match {
      case Some(polygon) => {
        val key = s"mosaic-extent-raw-$projectId-$zoom-${polygon.geom.envelope.xmax}-" +
          s"${polygon.geom.envelope.ymax}-${polygon.geom.envelope.xmin}-${polygon.geom.envelope.ymin}"
        rfCache.cachingOptionT(key) {
          MultiBandMosaic.rawForExtent(projectId, zoom, bbox)
        }
      }
      case _ => {
        val key = s"mosaic-extent-raw-$projectId-$zoom-nobbox"
        rfCache.cachingOptionT(key) {
          MultiBandMosaic.rawForExtent(projectId, zoom, bbox)
        }
      }
    }
  }
}
