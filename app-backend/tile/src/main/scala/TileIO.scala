package com.azavea.rf.tile.project

import java.util.UUID

import com.azavea.rf.common.cache._
import com.azavea.rf.common.cache.kryo.KryoMemcachedClient
import com.azavea.rf.common.{KamonTraceRF, Config => CommonConfig}
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.ScenesToProjects
import com.azavea.rf.datamodel.MosaicDefinition

import akka.dispatch.MessageDispatcher
import cats.data._
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.histogram._
import geotrellis.raster.io._
import geotrellis.slick.Projected
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.postgres.PostgresAttributeStore
import geotrellis.spark.io.s3._
import geotrellis.vector._
import spray.json.DefaultJsonProtocol._

import scala.concurrent._
import scala.util._

/** Trait with various IO operations for tiles and related metadata */
trait TileIO extends LazyLogging with KamonTraceRF {

  implicit val database: Database
  implicit val blockingDispatcher: MessageDispatcher
  val memcachedClient: KryoMemcachedClient
  val rfCache: CacheClient

  val cacheConfig = CommonConfig.memcached
  val store: PostgresAttributeStore

  /** Get histogram for a scene at a zoom level
    *
    * @param sceneId ID of scene to request histogram
    * @param zoom level of histogram requested
    * @return
    */
  def getSceneHistogram(sceneId: UUID, zoom: Int): OptionT[Future, Array[Histogram[Double]]] = {
    val key = s"layer-histogram-$sceneId-$zoom"
    rfCache.cachingOptionT(key, doCache = cacheConfig.layerAttributes.enabled)(
      OptionT(
        timedFuture("layer-histogram-source")(store.getHistogram[Array[Histogram[Double]]](LayerId(sceneId.toString, 0)))
      )
    )
  }

  /** Get highest zoom level for a scene
    *
    * @param sceneId ID of scene
    * @return
    */
  def getMaxZoomForScene(sceneId: UUID): OptionT[Future, Map[String, Int]] = {
    val cacheKey = s"max-zoom-for-layer-$sceneId"
    val layerIds = Set(sceneId)
    rfCache.cachingOptionT(cacheKey, doCache = cacheConfig.layerAttributes.enabled)(
      OptionT(
        timedFuture("layer-max-zoom-store")(store.maxZoomsForLayers(layerIds.map(_.toString)))
      )
    )
  }

  /** Get tile metadata for a scene at a given zoom level
    *
    * @param sceneId scene to request metadata for
    * @param zoom level of metadata desired
    * @return zoom level and metadata
    */
  def getSceneTileMetadata(sceneId: UUID, zoom: Int): OptionT[Future, (Int, TileLayerMetadata[SpatialKey])] = {

    logger.debug(s"Requesting tile layer metadata (layer: $sceneId, zoom: $zoom")
    rfCache.cachingOptionT(s"tile-metadata-$sceneId-$zoom")(getMaxZoomForScene(sceneId).mapFilter {
      case (pyramidMaxZoom) =>
        val layerName = sceneId.toString
        for (maxZoom <- pyramidMaxZoom.get(layerName)) yield {
          val z = if (zoom > maxZoom) maxZoom else zoom
          blocking {
            z -> store.readMetadata[TileLayerMetadata[SpatialKey]](LayerId(layerName, z))
          }
        }
    })
  }

  /** Get tile for given extent and zoom level
    *
    * @param sceneId ID of scene
    * @param zoom level to request
    * @param extent extent of tile requested
    * @return
    */
  def getSceneTileForExtent(sceneId: UUID, zoom: Int, extent: Extent): OptionT[Future, MultibandTile] = {
    val cacheKey = s"extent-tile-$sceneId-$zoom-${extent.xmin}-${extent.ymin}-${extent.xmax}-${extent.ymax}"
    OptionT(
      timedFuture("layer-for-tile-extent-cache")(
        rfCache.caching(cacheKey, doCache = cacheConfig.layerTile.enabled)( timedFuture("layer-for-tile-extent-s3")(
          Future {
            Try {
              S3CollectionLayerReader(store)
                .query[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](LayerId(sceneId.toString, zoom))
                .where(Intersects(extent))
                .result
                .stitch
                .crop(extent)
                .tile
                .resample(256, 256)
            } match {
              case Success(tile) => Option(tile)
              case Failure(e) =>
                logger.error(s"Query layer $sceneId at zoom $zoom for $extent: ${e.getMessage}")
                None
            }
          }
        ))
      )
    )
  }

  /** Retrieve mosaic definitions -- usually the first step in constructing a mosaic
    * for a project
    *
    * @param projectId project to request mosaic definition for
    * @param polygonOption optional polygon to restrict mosaid definitions for
    * @return
    */
  def getMosaicDefinition(projectId: UUID, polygonOption: Option[Projected[Polygon]]): OptionT[Future, Seq[MosaicDefinition]] = {
    logger.debug(s"Reading mosaic definition (project: $projectId")
    OptionT(timedFuture("get-mosaic-definition")(ScenesToProjects.getMosaicDefinition(projectId, polygonOption)))
  }

  /** Retrieve mosaic definitions -- usually the first step in constructing a mosaic
    * for a project
    *
    * @param projectId project to request mosaic definition for
    * @return
    */
  def getMosaicDefinition(projectId: UUID): OptionT[Future, Seq[MosaicDefinition]] = {
    getMosaicDefinition(projectId, None)
  }

  /** Get tile of scene for a zoom level and spatial key
    *
    * @param sceneId ID of scene
    * @param zoom level to request
    * @param key spatial key of tile
    * @return
    */
  def getSceneTile(sceneId: UUID, zoom: Int, key: SpatialKey): OptionT[Future, MultibandTile] = {
    val cacheKey = s"tile-$sceneId-$zoom-${key.col}-${key.row}"
    OptionT(rfCache.caching(cacheKey, doCache = cacheConfig.layerTile.enabled)(
      timedFuture("s3-tile-request")(Future {
        val reader = new S3ValueReader(store).reader[SpatialKey, MultibandTile](LayerId(sceneId.toString, zoom))
        Try {
          reader.read(key)
        } match {
          case Success(tile) => tile.some
          case Failure(e: ValueNotFoundError) => None
          case Failure(e) =>
            logger.error(s"Reading layer $sceneId at $key: ${e.getMessage}")
            None
        }
      })
    ))
  }

  /** Get tile of scene given an ID, zoom, column, and row
    *
    * @param sceneId ID of scene to request tile for
    * @param zoom level to request
    * @param col x coordinate of tile
    * @param row y coordinate of tile
    * @return
    */
  def getSceneTileZXY(sceneId: UUID, zoom: Int, col: Int, row: Int): OptionT[Future, MultibandTile] = {
    val tileLayerMetadata = getSceneTileMetadata(sceneId, zoom)
    tileLayerMetadata.flatMap {
      case (sourceZoom, tlm) =>
        val zoomDiff = zoom - sourceZoom
        logger.debug(s"Requesting layer tile (layer: $sceneId, zoom: $zoom, col: $col, row: $row, sourceZoom: $sourceZoom)")
        val resolutionDiff = 1 << zoomDiff
        val sourceKey = SpatialKey(col / resolutionDiff, row / resolutionDiff)
        if (tlm.bounds.includes(sourceKey)) {
          getSceneTile(sceneId, sourceZoom, sourceKey).map { tile =>
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
        } else {
          OptionT.none[Future, MultibandTile]
        }
    }
  }

  /** Fetch the tile for the given zoom level and bbox
    * If no bbox is specified, it will use the project tileLayerMetadata layoutExtent
    *
    * @param sceneId scene to request tile for an extent
    * @param zoom level to request data for
    * @param bbox optional bounding box to request data for
    * @param tileLayerMetadata metadata for tile
    * @return
    */
  def getSceneRenderedExtent(sceneId: UUID, zoom: Int, bbox: Option[Projected[Polygon]], tileLayerMetadata: OptionT[Future, (Int, TileLayerMetadata[SpatialKey])]): OptionT[Future, MultibandTile] =
    tileLayerMetadata.flatMap {
      case (sourceZoom, tlm) =>
        val extent: Extent = bbox.map {
          case Projected(poly, srid) =>
            poly.envelope.reproject(CRS.fromEpsgCode(srid), tlm.crs)
        }.getOrElse(tlm.layoutExtent)
        getSceneTileForExtent(sceneId, sourceZoom, extent)
    }

  /** Fetch all bands of a [[MultibandTile]] for the given extent and return them without assuming anything of their semantics
    *
    * @param projectId project to request a mosaic for
    * @param zoom level to request tiles for
    * @param bbox bounding box to optionally request data for
    * @param mosaicDefinition mosaic definition that defines how to transform tile data
    * @return
    */
  def getRawProjectMosaicForExtent(projectId: UUID, zoom: Int, bbox: Option[Projected[Polygon]], mosaicDefinition: OptionT[Future, Seq[MosaicDefinition]]): OptionT[Future, MultibandTile] =
    mosaicDefinition.flatMap { mosaic =>
      implicit val sceneIds = mosaic.map {
        case MosaicDefinition(sceneId, _) => sceneId
      }.toSet

      val mayhapTiles: Seq[OptionT[Future, MultibandTile]] =
        for (MosaicDefinition(sceneId, _) <- mosaic)
          yield {
            val tlm = getSceneTileMetadata(sceneId, zoom)
            getSceneRenderedExtent(sceneId, zoom, bbox, tlm)
          }

      val futureMergeTile: Future[Option[MultibandTile]] =
        Future.sequence(mayhapTiles.map(_.value)).map { maybeTiles =>
          val tiles = maybeTiles.flatten
          if (tiles.nonEmpty)
            Option(tiles.reduce(_ merge _))
          else
            Option.empty[MultibandTile]
        }

      OptionT(futureMergeTile)
    }
}
