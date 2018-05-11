package com.azavea.rf.common.utils

import com.azavea.rf.common.cache._
import com.azavea.rf.common.cache.kryo._
import com.azavea.rf.common.{Config => CommonConfig}
import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.crop._
import geotrellis.raster.resample._
import geotrellis.raster.reproject._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.reader.GeoTiffReader
import geotrellis.proj4._
import geotrellis.spark.tiling._
import scala.util.Properties
import scala.math
import scala.util.Try
import scala.concurrent._
import cats.data._
import cats.implicits._

object CogUtils {
  lazy val cacheConfig = CommonConfig.memcached
  lazy val memcachedClient = KryoMemcachedClient.DEFAULT
  lazy val rfCache = new CacheClient(memcachedClient)
  
  private val TmsLevels: Array[LayoutDefinition] = {
    val scheme = ZoomedLayoutScheme(WebMercator, 256)
    for (zoom <- 0 to 64) yield scheme.levelForZoom(zoom).layout
  }.toArray

  /** Read GeoTiff from URI while caching the header bytes in memcache */
  def fromUri(uri: String)(implicit ec: ExecutionContext): OptionT[Future, GeoTiff[MultibandTile]] = {
    val cacheKey = s"cog-header-${uri}"
    val cacheSize = 1<<18

    rfCache.cachingOptionT(cacheKey, doCache = cacheConfig.tool.enabled) {
      OptionT{ 
        Future { 
          RangeReaderUtils.fromUri(uri).map(_.readRange(0, cacheSize)) 
        } 
      }
    }.mapFilter { headerBytes =>
      RangeReaderUtils.fromUri(uri).map { rr =>
        val crr = CacheRangeReader(rr, headerBytes)
        GeoTiffReader.readMultiband(crr, decompress = false, streaming = true)
      }
    }
  }

  def fetch(uri: String, zoom: Int, x: Int, y: Int): Option[MultibandTile] =
    RangeReaderUtils.fromUri(uri).flatMap { rr =>
      val tiff = GeoTiffReader.readMultiband(rr, decompress = false, streaming = true)
      val transform = Proj4Transform(tiff.crs, WebMercator)
      val inverseTransform = Proj4Transform(WebMercator, tiff.crs)
      val tmsTileRE = RasterExtent(
        extent = TmsLevels(zoom).mapTransform.keyToExtent(x, y),
        cols = 256, rows = 256
      )
      val tiffTileRE = ReprojectRasterExtent(tmsTileRE, inverseTransform)

      if (tiffTileRE.extent.intersects(tiff.extent)) {
        val overview = GeoTiffUtils.closestTiffOverview(tiff, tiffTileRE.cellSize, Auto(0))
        val raster = GeoTiffUtils.cropGeoTiff(overview, tiffTileRE.extent)
        Some(raster.tile)
      } else None
    }


  def fetchForExtent(uri: String, zoom: Int, extent: Option[Extent]): Option[MultibandTile] = blocking {
    RangeReaderUtils.fromUri(uri).flatMap { rr =>
      val tiff = GeoTiffReader.readMultiband(rr, decompress = false, streaming = true)
      val transform = Proj4Transform(tiff.crs, WebMercator)
      val inverseTransform = Proj4Transform(WebMercator, tiff.crs)
      val actualExtent = extent.getOrElse(tiff.extent.reproject(tiff.crs, WebMercator))

      val tmsTileRE = RasterExtent(
        extent = actualExtent,
        cellSize = TmsLevels(zoom).cellSize
      )
      val tiffTileRE = ReprojectRasterExtent(tmsTileRE, inverseTransform)

      if (tiffTileRE.extent.intersects(tiff.extent)) {
        val overview = GeoTiffUtils.closestTiffOverview(tiff, tiffTileRE.cellSize, Auto(0))
        val raster = GeoTiffUtils.cropGeoTiff(overview, tiffTileRE.extent)
        Some(raster.tile)
      } else None
    }
  }
}

