package com.rasterfoundry.backsplash

import java.net.URLDecoder

import com.rasterfoundry.backsplash.color._
import geotrellis.vector.{io => _, _}
import geotrellis.raster.{io => _, _}
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.spark.SpatialKey
import geotrellis.spark.tiling.ZoomedLayoutScheme
import geotrellis.proj4.WebMercator
import geotrellis.contrib.vlm.geotiff.GeoTiffRasterSource
import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import geotrellis.contrib.vlm.RasterSource
import geotrellis.contrib.vlm.gdal.GDALRasterSource
import geotrellis.raster.MultibandTile
import geotrellis.vector.MultiPolygon
import scalacache.CatsEffect.modes._
import scalacache._
import scalacache.memoization._
import cats.effect.IO

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
  * @param projectLayerId UUID of the layer this image is a part of
  * @param uri location of the source data
  * @param subsetBands subset of bands to be read from source
  * @param corrections description + operations for how to correct image
  * @param singleBandOptions band + options of how to color a single band
  * @param mask geometry to limit the rendering
  */
final case class BacksplashGeotiff(
    imageId: UUID,
    @cacheKeyExclude projectLayerId: UUID,
    @cacheKeyExclude uri: String,
    subsetBands: List[Int],
    @cacheKeyExclude corrections: ColorCorrect.Params,
    @cacheKeyExclude singleBandOptions: Option[SingleBandOptions.Params],
    mask: Option[MultiPolygon],
    @cacheKeyExclude footprint: MultiPolygon)
    extends LazyLogging with BacksplashImage[IO] {

  implicit val tileCache = Cache.tileCache
  implicit val flags = Cache.tileCacheFlags
  implicit val rasterSourceCache = Cache.rasterSourceCache

  def getRasterSource: IO[RasterSource] = {
    if (enableGDAL) {
      logger.debug(s"Using GDAL Raster Source: ${uri}")
      // Do not bother caching - let GDAL internals worry about that
      IO {
        GDALRasterSource(URLDecoder.decode(uri, "UTF-8"))
      }
    } else {
      memoizeF(None) {
        logger.debug(s"Using GeoTiffRasterSource: ${uri}")
        IO {
          new GeoTiffRasterSource(uri)
        }
      }
    }
  }

  def readWithCache(z: Int, x: Int, y: Int)(
      implicit @cacheKeyExclude flags: Flags): IO[Option[MultibandTile]] =
    memoizeF(None) {
      logger.debug(s"Reading ${z}-${x}-${y} - Image: ${imageId} at ${uri}")
      val layoutDefinition = BacksplashImage.tmsLevels(z)
      getRasterSource.map { rasterSource =>
        logger.debug(s"CELL TYPE: ${rasterSource.cellType}")
        rasterSource
          .reproject(WebMercator)
          .tileToLayout(layoutDefinition, NearestNeighbor)
          .read(SpatialKey(x, y), subsetBands.toSeq) map { tile =>
          tile.mapBands((_: Int, t: Tile) => t.toArrayTile)
        }
      }.attempt.map {
        case Left(e)          => throw e
        case Right(multibandTile) => multibandTile
      }
    }

  def readWithCache(extent: Extent, cs: CellSize)(
      implicit @cacheKeyExclude flags: Flags
  ): IO[Option[MultibandTile]] = {
    memoizeF(None) {
      logger.debug(
        s"Reading Extent ${extent} with CellSize ${cs} - Image: ${imageId} at ${uri}"
      )
      val rasterExtent = RasterExtent(extent, cs)
      logger.debug(
        s"Expecting to read ${rasterExtent.cols * rasterExtent.rows} cells (${rasterExtent.cols} cols, ${rasterExtent.rows} rows)")
      getRasterSource.map { rasterSource =>
        rasterSource
          .reproject(WebMercator, NearestNeighbor)
          .resampleToGrid(GridExtent[Long](rasterExtent.extent,
                                           rasterExtent.cellSize),
                          NearestNeighbor)
          .read(extent, subsetBands)
          .map(_.tile)
      }.attempt.map {
        case Left(e)          => throw e
        case Right(multibandTile) => multibandTile
      }
    }
  }

  def selectBands(bands: List[Int]): BacksplashGeotiff = this.copy(subsetBands = bands)
}

sealed trait BacksplashImage[F[_]] extends LazyLogging {

  val footprint: MultiPolygon
  val imageId: UUID
  val subsetBands: List[Int]
  val corrections: ColorCorrect.Params
  val singleBandOptions: Option[SingleBandOptions.Params]
  val projectLayerId: UUID

  val enableGDAL = Config.RasterSource.enableGDAL

  /** Read ZXY tile - defers to a private method to enable disable/enabling of cache **/
  def read(z: Int, x: Int, y: Int): F[Option[MultibandTile]] = {
    readWithCache(z, x, y)
  }

  def readWithCache(z: Int, x: Int, y: Int)(
      implicit @cacheKeyExclude flags: Flags): F[Option[MultibandTile]]

  /** Read tile - defers to a private method to enable disable/enabling of cache **/
  def read(extent: Extent, cs: CellSize): F[Option[MultibandTile]] = {
    implicit val flags =
      Flags(Config.cache.tileCacheEnable, Config.cache.tileCacheEnable)
    logger.debug(s"Tile Cache Status: ${flags}")
    readWithCache(extent, cs)
  }

  def readWithCache(extent: Extent, cs: CellSize)(
      implicit @cacheKeyExclude flags: Flags
  ): F[Option[MultibandTile]]

  def getRasterSource: F[RasterSource]

  def getRasterSource(uri: String): RasterSource =
    throw new NotImplementedError(uri)

  def selectBands(bands: List[Int]): BacksplashImage[F]
}

object BacksplashImage {
  val tmsLevels = {
    val scheme = ZoomedLayoutScheme(WebMercator, 256)
    for (zoom <- 0 to 64) yield scheme.levelForZoom(zoom).layout
  }.toArray
}
