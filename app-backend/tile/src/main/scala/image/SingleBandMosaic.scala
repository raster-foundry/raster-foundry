package com.azavea.rf.tile.image

import com.azavea.rf.common.utils.TileUtils
import com.azavea.rf.common.cache.CacheClient
import com.azavea.rf.database.{SceneToProjectDao}
import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel.{ColorRampMosaic, Project, SingleBandOptions}
import com.azavea.rf.tile._

import com.typesafe.scalalogging.LazyLogging
import cats.data._
import cats.implicits._
import cats.effect.IO
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.GridBounds
import geotrellis.raster.histogram._
import geotrellis.slick.Projected
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.postgres.PostgresAttributeStore
import geotrellis.vector.{Extent, Polygon}

import scala.concurrent._
import java.util.UUID

import com.azavea.rf.database.util.RFTransactor


object SingleBandMosaic extends LazyLogging with KamonTrace {
  lazy val memcachedClient = LayerCache.memcachedClient
  implicit val xa = RFTransactor.xa
  val system = AkkaSystem.system
  implicit val blockingDispatcher =
    system.dispatchers.lookup("blocking-dispatcher")

  val store = PostgresAttributeStore()
  val rfCache = new CacheClient(memcachedClient)

  def tileLayerMetadata(id: UUID, zoom: Int)(
    implicit xa: Transactor[IO], sceneIds: Set[UUID]
  ): OptionT[Future, (Int, TileLayerMetadata[SpatialKey])] = {
    logger.debug(s"Requesting tile layer metadata (layer: $id, zoom: $zoom")
    LayerCache.maxZoomForLayers(Set(id)).mapFilter {
      case (pyramidMaxZoom) =>
        val layerName = id.toString
        for (maxZoom <- pyramidMaxZoom.get(layerName)) yield {
          val z = if (zoom > maxZoom) maxZoom else zoom
          blocking {
            z -> store.readMetadata[TileLayerMetadata[SpatialKey]](LayerId(layerName, z))
          }
        }
    }
  }

  /** SingleBandMosaic tiles from TMS pyramids given that they are in the same projection.
    *   If a layer does not go up to requested zoom it will be up-sampled.
    *   Layers missing color correction in the mosaic definition will be excluded.
    */
  def apply(
    project: Project, zoom: Int, col: Int, row: Int
  )(implicit xa: Transactor[IO]): OptionT[Future, MultibandTile] =
    traceName(s"SingleBandMosaic.apply(${project.id})") {
      logger.debug(s"Creating single band mosaic (project: ${project.id}, zoom: $zoom, col: $col, row: $row)")

      val polygonBbox: Projected[Polygon] = TileUtils.getTileBounds(zoom, col, row)
      project.singleBandOptions match {
        case Some(singleBandOptions: SingleBandOptions.Params) =>
          val futureTiles: Future[Seq[(MultibandTile, Array[Histogram[Double]])]] =
            SceneToProjectDao.query.filter(fr"project_id = ${project.id}").list.transact(xa).unsafeToFuture
              .map { _.map { _.sceneId }}
              .flatMap {
                sceneIds => {
                  Future.sequence(
                    sceneIds map { sceneId =>
                      SingleBandMosaic
                        .fetch(sceneId, zoom, col, row)(xa, sceneIds.toSet)
                        .value
                        .zip(LayerCache.layerHistogram(sceneId, zoom).value)
                        .map ({ case (tile, histogram) => for (a <- tile; b <- histogram) yield (a, b) })
                    }
                  ).map(_.flatten)
                }
              }
          val futureColoredTile =
            for {
              tiles: Seq[(MultibandTile, Array[Histogram[Double]])] <- futureTiles
            } yield ColorRampMosaic(tiles.toList, singleBandOptions)
          OptionT(futureColoredTile)
        case _ =>
          val message = "No valid single band render options found"
          logger.error(message)
          throw new IllegalArgumentException(message)
      }
    }

  def render(
    project: Project, zoomOption: Option[Int], bboxOption: Option[String], colorCorrect: Boolean
  )(implicit xa: Transactor[IO]): OptionT[Future, MultibandTile] =
    traceName(s"SingleBandMosaic.render(${project.id})") {
    val bboxPolygon: Option[Projected[Polygon]] =
      try {
        bboxOption map { bbox =>
          Projected(Extent.fromString(bbox).toPolygon(), 4326)
            .reproject(LatLng, WebMercator)(3857)
        }
      } catch {
        case e: Exception =>
          val message = "Four comma separated coordinates must be given for bbox"
          logger.error(message)
          throw new IllegalArgumentException(message).initCause(e)
      }
    val zoom: Int = zoomOption.getOrElse(8)
    project.singleBandOptions match {
      case Some(singleBandOptions: SingleBandOptions.Params) =>
        val futureTiles: Future[Seq[(MultibandTile, Array[Histogram[Double]])]] =
          SceneToProjectDao.query.filter(fr"project_id = ${project.id}").list.transact(xa).unsafeToFuture
            .map { _.map { _.sceneId } }
            .flatMap { sceneIds: Seq[UUID] =>
          Future.sequence(
              sceneIds map { sceneId: UUID =>
                SingleBandMosaic.fetchRenderedExtent(sceneId, zoom, bboxPolygon)(xa, sceneIds.toSet).value
              }
          ).map(_.flatten)
        }
        colorCorrect match {
          case true =>
            val futureColoredRender = for {
              tiles <- futureTiles
            } yield ColorRampMosaic(tiles.toList, singleBandOptions)
            OptionT(futureColoredRender)
          case false =>
            def processTiles(tiles: Seq[MultibandTile]): Option[MultibandTile] = {
              val singleBandTiles = tiles.map(_.band(singleBandOptions.band))
              singleBandTiles.isEmpty match {
                case true =>
                  None
                case _ =>
                  Some(MultibandTile(singleBandTiles.reduce(_ merge _)))
              }
            }

            val futureColoredRender = for {
              tiles: Seq[(MultibandTile, Array[Histogram[Double]])] <- futureTiles
            } yield processTiles(tiles.map(_._1))
            OptionT(futureColoredRender)
        }
      case None =>
        val message = "No valid single band render options found"
        logger.error(message)
        throw new IllegalArgumentException(message)
    }
  }

  /** Fetch the tile for given resolution. If it is not present, use a tile from a lower zoom level */
  def fetch(id: UUID, zoom: Int, col: Int, row: Int)(
      implicit xa: Transactor[IO],
      sceneIds: Set[UUID]
  ): OptionT[Future, MultibandTile] = {
    tileLayerMetadata(id, zoom).flatMap {
      case (sourceZoom, tlm) =>
        val zoomDiff = zoom - sourceZoom
        logger.debug(s"Requesting layer tile (layer: $id, zoom: $zoom, col: $col, row: $row, sourceZoom: $sourceZoom)")
        val resolutionDiff = 1 << zoomDiff
        val sourceKey = SpatialKey(col / resolutionDiff, row / resolutionDiff)
        if (tlm.bounds.includes(sourceKey)) {
          val tile = LayerCache.layerTile(id, sourceZoom, sourceKey).map { tile =>
            val innerCol = col % resolutionDiff
            val innerRow = row % resolutionDiff
            val cols = tile.cols / resolutionDiff
            val rows = tile.rows / resolutionDiff
            tile.crop(
              GridBounds(
                colMin = innerCol * cols,
                rowMin = innerRow * rows,
                colMax = (innerCol + 1) * cols - 1,
                rowMax = (innerRow + 1) * rows - 1
              )
            ).resample(256, 256)
          }
          tile

        } else {
          OptionT.none[Future, MultibandTile]
        }
    }
  }

  def fetchRenderedExtent(id: UUID, zoom: Int,  bbox: Option[Projected[Polygon]])(
    implicit xa: Transactor[IO], sceneIds: Set[UUID])
      : OptionT[Future, (MultibandTile, Array[Histogram[Double]])] =
    tileLayerMetadata(id, zoom).flatMap {
      case (sourceZoom, tlm) =>
        val extent: Extent =
          bbox
            .map {
              case Projected(poly, srid) =>
                poly.envelope.reproject(CRS.fromEpsgCode(srid), tlm.crs)
            }.getOrElse(tlm.layoutExtent)
        val hist = LayerCache.layerHistogram(id, sourceZoom)
        val tile = LayerCache.layerTileForExtent(id, sourceZoom, extent)
        for {
          t <- tile
          h <- hist
        } yield (t, h)
    }
}

