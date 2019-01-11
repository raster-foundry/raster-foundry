package com.rasterfoundry.backsplash

import java.net.URLDecoder

import com.rasterfoundry.backsplash.color._
import geotrellis.vector.{io => _, _}
import geotrellis.raster.{io => _, _}
import geotrellis.raster.histogram._
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.spark.SpatialKey
import geotrellis.proj4.WebMercator
import geotrellis.server.vlm.RasterSourceUtils
import geotrellis.contrib.vlm.geotiff.GeoTiffRasterSource
import io.circe.syntax._
import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import geotrellis.contrib.vlm.RasterSource
import geotrellis.contrib.vlm.gdal.GDALRasterSource
import geotrellis.raster.MultibandTile
import scalacache._
import scalacache.memoization._
import scalacache.modes.sync._

/** An image used in a tile or export service, can be color corrected, and requested a subet of the bands from the
  * image
  *
  * If caching is enabled then reads of the source tiles are cached. The image id, uri, subset of bands, single band
  * options, and either the z-x-y or extent is used to construct a unique key for the tile read.
  *
  * NOTE: additional class parameters added to this class that will NOT affect how the source data is read
  * need to be flagged with the @cacheKeyExclude decorator to avoid unecessarily namespacing values in the keys
  *
  * @param imageId UUID of the image (scene) in the database
  * @param projectId UUID of the layer this image is a part of
  * @param uri location of the source data
  * @param footprint extent of data the image covers
  * @param subsetBands subset of bands to be read from source
  * @param corrections description + operations for how to correct image
  * @param singleBandOptions band + options of how to color a single band
  */
case class BacksplashImage(
    imageId: UUID,
    @cacheKeyExclude projectId: UUID,
    @cacheKeyExclude uri: String,
    @cacheKeyExclude footprint: MultiPolygon,
    subsetBands: List[Int],
    @cacheKeyExclude corrections: ColorCorrect.Params,
    @cacheKeyExclude singleBandOptions: Option[SingleBandOptions.Params])
    extends LazyLogging {

  implicit val tileCache = Cache.tileCache
  implicit val flags = Cache.tileCacheFlags

  lazy val rasterSource = BacksplashImage.getRasterSource(uri)

  /** Read ZXY tile - defers to a private method to enable disable/enabling of cache **/
  def read(z: Int, x: Int, y: Int): Option[MultibandTile] = {
    readWithCache(z, x, y)
  }

  private def readWithCache(z: Int, x: Int, y: Int)(
      implicit @cacheKeyExclude flags: Flags): Option[MultibandTile] =
    memoizeSync(None) {
      logger.debug(s"Reading ${z}-${x}-${y} - Image: ${imageId} at ${uri}")
      val layoutDefinition = BacksplashImage.tmsLevels(z)
      logger.debug(s"CELL TYPE: ${rasterSource.cellType}")
      rasterSource
        .reproject(WebMercator)
        .tileToLayout(layoutDefinition, NearestNeighbor)
        .read(SpatialKey(x, y), subsetBands) map { tile =>
        tile.mapBands((n: Int, t: Tile) => t.toArrayTile)
      }
    }

  /** Read tile - defers to a private method to enable disable/enabling of cache **/
  def read(extent: Extent, cs: CellSize): Option[MultibandTile] = {
    implicit val flags =
      Flags(Config.cache.tileCacheEnable, Config.cache.tileCacheEnable)
    logger.debug(s"Tile Cache Status: ${flags}")
    readWithCache(extent, cs)
  }

  private def readWithCache(extent: Extent, cs: CellSize)(
      implicit @cacheKeyExclude flags: Flags
  ): Option[MultibandTile] = {
    memoizeSync(None) {
      logger.debug(
        s"Reading Extent ${extent} with CellSize ${cs} - Image: ${imageId} at ${uri}"
      )
      val rasterExtent = RasterExtent(extent, cs)
      logger.debug(
        s"Expecting to read ${rasterExtent.cols * rasterExtent.rows} cells (${rasterExtent.cols} cols, ${rasterExtent.rows} rows)")
      rasterSource
        .reproject(WebMercator, NearestNeighbor)
        .resampleToGrid(rasterExtent, NearestNeighbor)
        .read(extent, subsetBands.toSeq)
        .map(_.tile)
    }
  }
}

object BacksplashImage extends RasterSourceUtils with LazyLogging {

  implicit val rasterSourceCache = Cache.rasterSourceCache
  implicit val flags = Cache.rasterSourceCacheFlags

  val enableGDAL = Config.RasterSource.enableGDAL

  def getRasterSource(uri: String): RasterSource = {
    if (enableGDAL) {
      logger.debug(s"Using GDAL Raster Source: ${uri}")
      // Do not bother caching - let GDAL internals worry about that
      GDALRasterSource(URLDecoder.decode(uri))
    } else {
      memoizeSync(None) {
        logger.debug(s"Using GeoTiffRasterSource: ${uri}")
        val rs = new GeoTiffRasterSource(uri)
        // access lazy vals so they are cached
        rs.tiff
        rs.rasterExtent
        rs.resolutions
        rs
      }
    }
  }
}
