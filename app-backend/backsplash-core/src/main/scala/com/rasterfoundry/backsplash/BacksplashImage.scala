package com.rasterfoundry.backsplash

import java.net.URLDecoder

import com.rasterfoundry.common.color._
import com.rasterfoundry.datamodel.{SceneMetadataFields, SingleBandOptions}
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
import cats.implicits._
import com.colisweb.tracing.TracingContext

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
    @cacheKeyExclude projectId: UUID,
    @cacheKeyExclude projectLayerId: UUID,
    @cacheKeyExclude uri: String,
    subsetBands: List[Int],
    @cacheKeyExclude corrections: ColorCorrect.Params,
    singleBandOptions: Option[SingleBandOptions.Params],
    mask: Option[MultiPolygon],
    @cacheKeyExclude footprint: MultiPolygon,
    metadata: SceneMetadataFields,
    @cacheKeyExclude tracingContext: TracingContext[IO])
    extends LazyLogging
    with BacksplashImage[IO] {

  val tags: Map[String, String] = Map(
    "imageId" -> imageId.toString,
    "projectId" -> projectId.toString,
    "projectLayerId" -> projectLayerId.toString,
    "uri" -> uri
  )

  implicit val tileCache = Cache.tileCache
  implicit val rasterSourceCache = Cache.rasterSourceCache

  def getRasterSource(context: TracingContext[IO]): IO[RasterSource] = {
    implicit val rasterSourceCacheFlags = Cache.rasterSourceCacheFlags
    context.childSpan("getRasterSource", tags) use { _ =>
      if (enableGDAL) {
        logger.debug(s"Using GDAL Raster Source: ${uri}")
        // Do not bother caching - let GDAL internals worry about that
        val rasterSource = GDALRasterSource(URLDecoder.decode(uri, "UTF-8"))
        IO {
          metadata.noDataValue match {
            case Some(nd) =>
              rasterSource.interpretAs(DoubleUserDefinedNoDataCellType(nd))
            case _ =>
              rasterSource
          }
        }
      } else {
        memoizeF(None) {
          logger.debug(s"Using GeoTiffRasterSource: ${uri}")
          val rasterSource = new GeoTiffRasterSource(uri)
          IO {
            metadata.noDataValue match {
              case Some(nd) =>
                rasterSource.interpretAs(DoubleUserDefinedNoDataCellType(nd))
              case _ =>
                rasterSource
            }
          }
        }
      }
    }
  }

  def readWithCache(z: Int, x: Int, y: Int, context: TracingContext[IO])(
      implicit @cacheKeyExclude flags: Flags): IO[Option[MultibandTile]] = {
    val readTags = tags.combine(Map("zoom" -> z.toString))
    context.childSpan("cache.read(z, x, y)", readTags) use { childContext =>
      memoizeF(None) {
        val layoutDefinition = BacksplashImage.tmsLevels(z)
        for {
          rasterSource <- getRasterSource(childContext)
          tile <- childContext.childSpan("rasterSource.read") use { _ =>
            IO(
              rasterSource
                .reproject(WebMercator)
                .tileToLayout(layoutDefinition, NearestNeighbor)
                .read(SpatialKey(x, y), subsetBands)
            ).map(_.map { tile =>
                tile.mapBands((_: Int, t: Tile) => t.toArrayTile)
              })
              .attempt
          }
        } yield {
          tile match {
            case Left(e)              => throw e
            case Right(multiBandTile) => multiBandTile
          }
        }
      }
    }
  }

  def readWithCache(extent: Extent, cs: CellSize, context: TracingContext[IO])(
      implicit @cacheKeyExclude flags: Flags
  ): IO[Option[MultibandTile]] = {
    val readTags =
      tags.combine(Map("extent" -> extent.toString, "cellSize" -> cs.toString))
    context.childSpan("cache.read(extent, cs)", readTags) use { child =>
      memoizeF(None) {
        val rasterExtent = RasterExtent(extent, cs)
        logger.debug(
          s"Expecting to read ${rasterExtent.cols * rasterExtent.rows} cells (${rasterExtent.cols} cols, ${rasterExtent.rows} rows)")
        for {
          rasterSource <- getRasterSource(child)
          tile <- child.childSpan("rasterSource.read(extent, cs)", readTags) use {
            _ =>
              IO(
                rasterSource
                  .reproject(WebMercator, NearestNeighbor)
                  .resampleToGrid(GridExtent[Long](rasterExtent.extent,
                                                   rasterExtent.cellSize),
                                  NearestNeighbor)
                  .read(extent, subsetBands)
                  .map(_.tile)).attempt
                .map {
                  case Left(e)              => throw e
                  case Right(multibandTile) => multibandTile
                }
          }
        } yield {
          tile
        }
      }
    }
  }

  def selectBands(bands: List[Int]): BacksplashGeotiff =
    this.copy(subsetBands = bands)
}

sealed trait BacksplashImage[F[_]] extends LazyLogging {

  val footprint: MultiPolygon
  val imageId: UUID
  val subsetBands: List[Int]
  val corrections: ColorCorrect.Params
  val singleBandOptions: Option[SingleBandOptions.Params]
  val projectId: UUID
  val projectLayerId: UUID
  val mask: Option[MultiPolygon]
  val metadata: SceneMetadataFields
  val tracingContext: TracingContext[F]

  val enableGDAL = Config.RasterSource.enableGDAL

  /** Read ZXY tile - defers to a private method to enable disable/enabling of cache **/
  def read(z: Int,
           x: Int,
           y: Int,
           context: TracingContext[IO]): F[Option[MultibandTile]] = {
    implicit val flags =
      Flags(Config.cache.tileCacheEnable, Config.cache.tileCacheEnable)
    readWithCache(z, x, y, context)
  }

  def readWithCache(z: Int, x: Int, y: Int, context: TracingContext[IO])(
      implicit @cacheKeyExclude flags: Flags): F[Option[MultibandTile]]

  /** Read tile - defers to a private method to enable disable/enabling of cache **/
  def read(extent: Extent,
           cs: CellSize,
           context: TracingContext[IO]): F[Option[MultibandTile]] = {
    implicit val flags =
      Flags(Config.cache.tileCacheEnable, Config.cache.tileCacheEnable)
    logger.debug(s"Tile Cache Status: ${flags}")
    readWithCache(extent, cs, context)
  }

  def readWithCache(extent: Extent, cs: CellSize, context: TracingContext[IO])(
      implicit @cacheKeyExclude flags: Flags
  ): F[Option[MultibandTile]]

  def getRasterSource(context: TracingContext[F]): F[RasterSource]

  def getRasterSource(uri: String, context: TracingContext[F]): RasterSource = {
    logger.error(s"Error for Trace: ${context.traceId}")
    throw new NotImplementedError(uri)
  }

  def selectBands(bands: List[Int]): BacksplashImage[F]
}

object BacksplashImage {
  val tmsLevels = {
    val scheme = ZoomedLayoutScheme(WebMercator, 256)
    for (zoom <- 0 to 64) yield scheme.levelForZoom(zoom).layout
  }.toArray
}
