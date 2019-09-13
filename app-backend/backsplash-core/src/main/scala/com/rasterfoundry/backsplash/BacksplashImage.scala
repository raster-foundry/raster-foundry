package com.rasterfoundry.backsplash

import com.rasterfoundry.backsplash.error.RequirementFailedException
import com.rasterfoundry.common.color._
import com.rasterfoundry.datamodel.{SceneMetadataFields, SingleBandOptions}

import cats.{Monad, MonadError, Parallel}
import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO, Sync}
import cats.implicits._
import com.colisweb.tracing.TracingContext
import com.typesafe.scalalogging.LazyLogging
import geotrellis.contrib.vlm.RasterSource
import geotrellis.contrib.vlm.gdal.GDALRasterSource
import geotrellis.contrib.vlm.geotiff.GeoTiffRasterSource
import geotrellis.proj4.WebMercator
import geotrellis.raster.histogram._
import geotrellis.raster.MultibandTile
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.raster.{io => _, _}
import geotrellis.spark.SpatialKey
import geotrellis.spark.tiling.ZoomedLayoutScheme
import geotrellis.vector.MultiPolygon
import geotrellis.vector.{io => _, _}
import scalacache.CatsEffect.modes._
import scalacache.{MonadError => _, _}
import scalacache.memoization._

import java.net.URLDecoder
import java.util.UUID

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

case class Landsat8MultiTiffImage(
    imageId: UUID,
    footprint: MultiPolygon,
    subsetBands: List[Int],
    corrections: ColorCorrect.Params,
    singleBandOptions: Option[SingleBandOptions.Params],
    projectId: UUID,
    projectLayerId: UUID,
    mask: Option[MultiPolygon],
    prefix: String,
    tracingContext: TracingContext[IO]
)(implicit contextShift: ContextShift[IO])
    extends MultiTiffImage[IO, IO.Par] {

  val metadata = SceneMetadataFields()
  val imageName: Option[String] = prefix.split("/").lastOption
  val tags = Map(
    "imageName" -> (imageName getOrElse ""),
    "imageId" -> s"$imageId",
    "subsetBands" -> subsetBands.mkString(","),
    "prefix" -> prefix,
    "readType" -> "LandsatMultitiff"
  )

  def getUri(band: Int): Option[String] = imageName map { name =>
    s"$prefix/${name}_B${band + 1}.TIF"
  }

  def getBandRasterSource(i: Int,
                          context: TracingContext[IO]): IO[RasterSource] = {
    val uri = getUri(i) getOrElse { "" }
    val rsTags = tags.combine(Map("uri" -> uri))
    context.childSpan("getBandRasterSource", rsTags) use { _ =>
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
    }
  }

  def selectBands(bands: List[Int]) = this.copy(subsetBands = bands)
}

sealed abstract class MultiTiffImage[F[_]: Monad, G[_]](
    implicit F: Parallel[F, G],
    sync: Sync[F],
    mode: Mode[F],
    Err: MonadError[F, Throwable]
) extends BacksplashImage[F]
    with LazyLogging {

  implicit val tileCache = Cache.tileCache
  implicit val rasterSourceCache = Cache.rasterSourceCache

  val tags: Map[String, String]

  def getRasterSource(context: TracingContext[F]): F[RasterSource] =
    context.childSpan("getRasterSource", tags) use { child =>
      getBandRasterSource(subsetBands.headOption getOrElse 0, child)
    }

  /** Get a single band raster source for one band of this image
    *
    * MultiTiff image assumes that you have a single scene split up over several
    * single band tiffs, e.g., how Landast 8 and Sentinel-2 are stored on AWS.
    * This assumption will cause some things to break if you end up trying to make
    * a MultiTiffImage out of a multi-band tiff, e.g., you might think you're going
    * to color correct one way and have something else happen entirely.
    */
  def getBandRasterSource(i: Int, context: TracingContext[F]): F[RasterSource]

  def getBandRasterSources(
      bs: NonEmptyList[Int],
      context: TracingContext[F]
  ): F[NonEmptyList[RasterSource]] =
    bs parTraverse { band =>
      getBandRasterSource(band, context)
    }

  def getHistogram(context: TracingContext[F]): F[Array[Histogram[Double]]] =
    context.childSpan("readHistogramFromSource", Map.empty) use { child =>
      for {
        sources <- subsetBands.toNel match {
          case Some(bs) => getBandRasterSources(bs, child)
          case None =>
            Err.raiseError(
              RequirementFailedException("Must request at least one band"))
        }
        zoomedOutExtents <- sources parTraverse { rs =>
          sync.delay {
            // This is at this point just throwing garbag at the problem of how to get
            // a reasonable sample
            // For some reason the tiles coming back from this believe that their min / max is 0,
            // which causes problems when trying to normalize before
            rs.resolutions.take(rs.resolutions.length - 2).lastOption flatMap {
              gridExtent =>
                println(s"Resolution selected: $gridExtent")
                println(
                  s"Resolution stats: cols -- ${gridExtent.cols}, rows -- ${gridExtent.rows}")
                println(
                  s"Grid bounds stats: ${gridExtent.gridBoundsFor(gridExtent.extent)}")
                // rs.read(gridExtent.extent, List(0))
                rs.read(gridExtent.gridBoundsFor(gridExtent.extent), List(0))
            }
          }
        }
      } yield {
        zoomedOutExtents collect {
          case Some(t) => {
            val tile = t.tile.band(0)
            val (oldMin, oldMax) = tile.findMinMaxDouble
            println(s"Old min: $oldMin, oldMax: $oldMax")
            tile.normalize(oldMin, oldMax, 0, 255).histogramDouble
          }
        } toArray
      }
    }

  def readWithCache(z: Int, x: Int, y: Int, context: TracingContext[F])(
      implicit @cacheKeyExclude flags: Flags): F[Option[MultibandTile]] = {
    val readTags = tags.combine(Map("zoom" -> z.toString))
    context.childSpan("cache.read(z, x, y)", readTags) use { child =>
      memoizeF(None) {
        val layoutDefinition = BacksplashImage.tmsLevels(z)
        for {
          sources <- subsetBands.toNel match {
            case Some(bs) => getBandRasterSources(bs, child)
            case None =>
              Err.raiseError(
                RequirementFailedException("Must request at least one band"))
          }
          tiles <- sources parTraverse { rs =>
            child.childSpan("readFromRasterSource",
                            Map("rasterSourcePath" -> s"${rs.dataPath}")) use {
              _ =>
                sync.delay {
                  rs.reproject(WebMercator)
                    .tileToLayout(layoutDefinition, NearestNeighbor)
                    .read(SpatialKey(x, y), List(0)) map { tile =>
                    tile
                      .mapBands((_: Int, t: Tile) => t.toArrayTile)
                      .band(0)
                  }
                }
            }
          }
        } yield {
          Some(MultibandTile(tiles.toList collect { case Some(t) => t }))
        }
      }
    }
  }

  def readWithCache(extent: Extent, cs: CellSize, context: TracingContext[F])(
      implicit flags: scalacache.Flags): F[Option[MultibandTile]] = {
    val readTags =
      tags.combine(Map("extent" -> extent.toString, "cellSize" -> cs.toString))
    context.childSpan("cache.read(extent, cs)", readTags) use { child =>
      memoizeF(None) {
        logger.debug(
          s"Reading Extent ${extent} with CellSize ${cs} - Image: ${imageId}"
        )
        val rasterExtent = RasterExtent(extent, cs)
        logger.debug(
          s"Expecting to read ${rasterExtent.cols * rasterExtent.rows} cells (${rasterExtent.cols} cols, ${rasterExtent.rows} rows)"
        )
        for {
          sources <- subsetBands.toNel match {
            case Some(rs) => getBandRasterSources(rs, child)
            case None =>
              Err.raiseError(
                RequirementFailedException("Must request at least one band"))
          }
          tiles <- sources parTraverse { rs =>
            sync.delay {
              rs.reproject(WebMercator, NearestNeighbor)
                .resampleToGrid(
                  GridExtent[Long](rasterExtent.extent, rasterExtent.cellSize),
                  NearestNeighbor
                )
                .read(extent, List(0))
                .map(_.tile.band(0))
            }
          }
        } yield {
          Some(MultibandTile(tiles.toList collect { case Some(t) => t }))
        }
      }
    }
  }
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
           context: TracingContext[F]): F[Option[MultibandTile]] = {
    implicit val flags =
      Flags(Config.cache.tileCacheEnable, Config.cache.tileCacheEnable)
    readWithCache(z, x, y, context)
  }

  def readWithCache(z: Int, x: Int, y: Int, context: TracingContext[F])(
      implicit @cacheKeyExclude flags: Flags): F[Option[MultibandTile]]

  /** Read tile - defers to a private method to enable disable/enabling of cache **/
  def read(extent: Extent,
           cs: CellSize,
           context: TracingContext[F]): F[Option[MultibandTile]] = {
    implicit val flags =
      Flags(Config.cache.tileCacheEnable, Config.cache.tileCacheEnable)
    logger.debug(s"Tile Cache Status: ${flags}")
    readWithCache(extent, cs, context)
  }

  def readWithCache(extent: Extent, cs: CellSize, context: TracingContext[F])(
      implicit @cacheKeyExclude flags: Flags
  ): F[Option[MultibandTile]]

  def getRasterSource(context: TracingContext[F]): F[RasterSource]

  def selectBands(bands: List[Int]): BacksplashImage[F]
}

object BacksplashImage {
  val tmsLevels = {
    val scheme = ZoomedLayoutScheme(WebMercator, 256)
    for (zoom <- 0 to 64) yield scheme.levelForZoom(zoom).layout
  }.toArray
}
