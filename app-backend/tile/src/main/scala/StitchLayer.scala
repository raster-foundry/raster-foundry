package com.azavea.rf.tile

import com.azavea.rf.database.Database
import com.azavea.rf.common.cache._

import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.vector._
import geotrellis.vector.io._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.s3._
import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.util.{Try, Success, Failure}


object StitchLayer extends LazyLogging with Config {
  implicit val memcachedClient = LayerCache.memcachedClient
  implicit val database = Database.DEFAULT

  /** This function will iterate through zoom levels a layer, starting with 1, until it finds the level
    * at which the data pixels stored in the layer cover at least size pixels in columns or rows.
    * Because the layer extent can be skewed or skinny and warping is not an option we can't demand both cols and rows.
    *
    * Next it will pull the tiles stored at this zoom level, stitch them together and crop to layer extent.
    * This can be used to generate a thumbnail when asking for a small sample, which will use high zoom level.
    * This also can be used to export an image of the layer at higher resolution.
    * However it is possible to overload this function when asking for a sample of too large a size.
    *
    * Because this is an expensive operation the stitched tile is cached.
    * For non-cached version use [[stitch]] function.
    */
  val stitchCache = HeapBackedMemcachedClient[Option[MultibandTile]](memcachedClient)
  def apply(id: RfLayerId, size: Int): Future[Option[MultibandTile]] =
    stitchCache.caching(s"stitch-{$size}") {
      for {
        prefix <- id.prefix
        store <- LayerCache.attributeStore(prefix)
      }
      yield stitch(store, id.sceneId.toString, size)
    }

  def stitch(store: AttributeStore, layerName: String, size: Int): Option[MultibandTile] = {
    require(size < 4096, s"$size is too large to stitch")
    minZoomLevel(store, layerName, size).map { case (layerId, re) =>
      logger.debug(s"Stitching from $layerId, ${re.extent.reproject(WebMercator, LatLng).toGeoJson}")
      S3CollectionLayerReader(store)
        .query[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](layerId)
        .where(Intersects(re.extent))
        .result
        .stitch
        .crop(re.extent)
    } match {
      case Success(tile) => Some(tile)
      case Failure(_) => None
    }
  }

  /** Find the minimum zoom level with enough pixels covering the data region of the layer */
  def minZoomLevel(store: AttributeStore, layerName: String, size: Int): Try[(LayerId, RasterExtent)] = {
    def forZoom(zoom: Int): Try[(LayerId, RasterExtent)] = {
      val currentId = LayerId(layerName, zoom)
      val meta = Try { store.readMetadata[TileLayerMetadata[SpatialKey]](currentId) }
      val rasterExtent = meta.map { tlm => (tlm, dataRasterExtent(tlm)) }
      rasterExtent.map { case(tlm, re) =>
        logger.info(s"Data Extent: ${tlm.extent.reproject(WebMercator, LatLng).toGeoJson()}")
        logger.debug(s"$currentId has (${re.cols},${re.rows}) pixels")
        if (re.cols >= size || re.rows >= size) (currentId, re)
        else forZoom(zoom + 1).getOrElse((currentId, re))
      }
    }
    forZoom(1)
  }

  def dataRasterExtent(md: TileLayerMetadata[_]): RasterExtent = {
    val re = RasterExtent(md.layout.extent,
      md.layout.tileLayout.totalCols.toInt,
      md.layout.tileLayout.totalRows.toInt)
    val gb = re.gridBoundsFor(md.extent)
    re.rasterExtentFor(gb).toRasterExtent
  }
}
