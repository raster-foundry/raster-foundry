package com.azavea.rf.common.utils

import com.azavea.rf.common.cache._
import com.azavea.rf.common.cache.kryo._
import com.azavea.rf.common.{Config => CommonConfig}

import com.amazonaws.services.s3.AmazonS3URI
import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.crop._
import geotrellis.raster.resample._
import geotrellis.raster.histogram._
import geotrellis.raster.reproject._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.reader.GeoTiffReader
import geotrellis.util._
import geotrellis.proj4._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.s3._
import geotrellis.spark.io.s3.util._
import geotrellis.spark.tiling._
import geotrellis.vector.Projected

import org.apache.spark.{HashPartitioner, SparkContext}
import org.apache.spark.rdd.RDD

import cats.data._
import cats.implicits._

import scala.concurrent._
import java.net.URLDecoder

object CogUtils {
  lazy val cacheConfig = CommonConfig.memcached
  lazy val memcachedClient = KryoMemcachedClient.default
  lazy val rfCache = new CacheClient(memcachedClient)

  private val TmsLevels: Array[LayoutDefinition] = {
    val scheme = ZoomedLayoutScheme(WebMercator, 256)
    for (zoom <- 0 to 64) yield scheme.levelForZoom(zoom).layout
  }.toArray

  /** Read GeoTiff from URI while caching the header bytes in memcache */
  def fromUri(uri: String)(implicit ec: ExecutionContext)
    : OptionT[Future, GeoTiff[MultibandTile]] = {
    val cacheKey = s"cog-header-${URIUtils.withNoParams(uri)}"
    val cacheSize = 1 << 18

    rfCache
      .cachingOptionT(cacheKey, doCache = cacheConfig.tool.enabled) {
        OptionT {
          Future {
            RangeReaderUtils.fromUri(uri).map(_.readRange(0, cacheSize))
          }
        }
      }
      .mapFilter { headerBytes =>
        RangeReaderUtils.fromUri(uri).map { rr =>
          val crr = CacheRangeReader(rr, headerBytes)
          GeoTiffReader.readMultiband(crr, streaming = true)
        }
      }
  }

  def fromUriAsRdd(uri: String)(implicit sc: SparkContext) = {
    val maxTileSize = 256
    val pixelBuffer = 16
    val partitionBytes = pixelBuffer * 1024 * 1024
    def readInfo = {
      GeoTiffReader.readGeoTiffInfo(
        RangeReaderUtils
          .fromUri(uri)
          .getOrElse(
            throw new IllegalArgumentException(
              s"Unable to create range reader for uri $uri")
          ),
        streaming = true,
        withOverviews = true
      )
    }

    val info = readInfo

    // This listing can be masked by Geometry if desired
    val windows: Array[GridBounds] = info.segmentLayout
      .listWindows(maxTileSize)
      .map(_.buffer(pixelBuffer))

    val partitions: Array[Array[GridBounds]] =
      info.segmentLayout.partitionWindowsBySegments(
        windows,
        partitionBytes / math.max(info.cellType.bytes, 1))

    val layoutScheme = FloatingLayoutScheme(maxTileSize)

    val projectedExtentRDD: RDD[(ProjectedExtent, MultibandTile)] =
      sc.parallelize(partitions, partitions.length).flatMap { bounds =>
        // re-constructing here to avoid serialization pit-falls
        val info = readInfo
        val geoTiff = GeoTiffReader.geoTiffMultibandTile(info)
        val window = geoTiff.crop(bounds.filter(geoTiff.gridBounds.intersects))

        window.map {
          case (bound, tile) =>
            val extent = info.rasterExtent.extentFor(bound, clamp = false)
            ProjectedExtent(extent, info.crs) -> tile
        }
      }

    val (_: Int, metadata: TileLayerMetadata[SpatialKey]) =
      projectedExtentRDD.collectMetadata[SpatialKey](layoutScheme)

    val tilerOptions =
      Tiler.Options(
        resampleMethod = Bilinear,
        partitioner = new HashPartitioner(projectedExtentRDD.partitions.length)
      )

    val tiledRdd =
      projectedExtentRDD.tileToLayout[SpatialKey](metadata, tilerOptions)

    ContextRDD(tiledRdd, metadata)
  }

  def fetch(uri: String, zoom: Int, x: Int, y: Int)(
      implicit ec: ExecutionContext): OptionT[Future, MultibandTile] =
    rfCache.cachingOptionT(
      s"cog-tile-${zoom}-${x}-${y}-${URIUtils.withNoParams(uri)}")(
      CogUtils.fromUri(uri).mapFilter { tiff =>
        val transform = Proj4Transform(tiff.crs, WebMercator)
        val inverseTransform = Proj4Transform(WebMercator, tiff.crs)
        val tmsTileRE = RasterExtent(
          extent = TmsLevels(zoom).mapTransform.keyToExtent(x, y),
          cols = 256,
          rows = 256
        )
        val tiffTileRE = ReprojectRasterExtent(tmsTileRE, inverseTransform)
        val overview =
          closestTiffOverview(tiff, tiffTileRE.cellSize, AutoHigherResolution)
        cropGeoTiff(overview, tiffTileRE.extent).map { raster =>
          raster.reproject(tmsTileRE, transform, inverseTransform).tile
        }
      }
    )

  // To construct a multiband geotiff, we have to have more than one band, so the fact that
  // we have one at all guarantees that bands.head is defined
  @SuppressWarnings(Array("TraversableHead"))
  def thumbnail(uri: String,
                widthO: Option[Int],
                heightO: Option[Int],
                redO: Option[Int],
                greenO: Option[Int],
                blueO: Option[Int],
                floorO: Option[Int])(
      implicit ec: ExecutionContext): OptionT[Future, MultibandTile] = {
    def trim: Int => Int = Math.min(_, 512)
    // Check width and height parameters, defaulting to 256 if neither is provided, otherwise
    // trimming to [0, 512]. Widths and heights are approximate anyway, with the actual size of
    // the returned PNG determined by the size of the closest overview to the cell size implied by
    // the requested width and height.
    val (width, height) = (widthO, heightO) match {
      case (Some(w), Some(h)) => (trim(w), trim(h))
      case (Some(w), None)    => (trim(w), trim(w))
      case (None, Some(h))    => (trim(h), trim(h))
      case _                  => (256, 256)
    }
    val (red, green, blue) =
      (redO.getOrElse(0), greenO.getOrElse(1), blueO.getOrElse(2))
    // If no floor passed, set floor to 25 to do some minimal brightening to the image
    val floor = floorO.getOrElse(25)
    rfCache.cachingOptionT(s"cog-thumbnail-${width}-${height}-${URIUtils
      .withNoParams(uri)}-${red}-${green}-${blue}-${floor}")(
      CogUtils.fromUri(uri).mapFilter {
        tiff =>
          val cellSize = CellSize(tiff.extent, width, height)
          val overview =
            closestTiffOverview(tiff, cellSize, AutoHigherResolution)
          val overviewRasterCellSize = overview.raster.cellSize
          val overviewExtentWidth = overview.extent.width
          val overviewExtentHeight = overview.extent.height
          val normalized = tiff.tile.bandCount match {
            case x if x >= 3 => {
              val tile = Raster(overview.tile, overview.extent).tile
                .subsetBands(red, green, blue)
              tile.bands map {
                (b: Tile) =>
                  {
                    val tileO = (b.histogram.minValue, b.histogram.maxValue).tupled map {
                      case (minVal, maxVal) if maxVal > minVal =>
                        b.normalize(minVal, maxVal, floor, 255)
                      case (minVal, maxVal) =>
                        b.normalize(minVal, minVal + 255, floor, 255)
                    }
                    tileO.getOrElse(
                      IntArrayTile.fill(0, b.cols, b.rows).convert(b.cellType))
                  }
              }
            }
            case x => {
              val tile = overview.tile.bands.head
              val normalizedO = (tile.histogram.minValue,
                                 tile.histogram.maxValue).tupled map {
                case (minVal, maxVal) =>
                  tile.normalize(minVal, maxVal, floor, 255)
              }
              Vector(
                normalizedO.getOrElse(
                  IntArrayTile
                    .fill(0, tile.cols, tile.rows)
                    .convert(tile.cellType)
                )
              )
            }
          }
          Some(MultibandTile(normalized))
      }
    )
  }

  def cropForZoomExtent(tiff: GeoTiff[MultibandTile],
                        zoom: Int,
                        extent: Option[Extent])(
      implicit ec: ExecutionContext): OptionT[Future, MultibandTile] = {
    val transform = Proj4Transform(tiff.crs, WebMercator)
    val inverseTransform = Proj4Transform(WebMercator, tiff.crs)
    val actualExtent =
      extent.getOrElse(tiff.extent.reproject(tiff.crs, WebMercator))
    val tmsTileRE =
      RasterExtent(extent = actualExtent, cellSize = TmsLevels(zoom).cellSize)
    val tiffTileRE = ReprojectRasterExtent(tmsTileRE, inverseTransform)
    val overview =
      closestTiffOverview(tiff, tiffTileRE.cellSize, AutoHigherResolution)

    OptionT(Future(cropGeoTiff(overview, tiffTileRE.extent).map { raster =>
      raster.reproject(tmsTileRE, transform, inverseTransform).tile
    }))
  }

  /** Work around GeoTiff.closestTiffOverview being private to geotrellis */
  def closestTiffOverview[T <: CellGrid](
      tiff: GeoTiff[T],
      cs: CellSize,
      strategy: OverviewStrategy): GeoTiff[T] = {
    geotrellis.hack.GTHack.closestTiffOverview(tiff, cs, strategy)
  }

  def getTiffExtent(uri: String): Option[Projected[MultiPolygon]] = {
    for {
      rr <- RangeReaderUtils.fromUri(uri)
      tiff = GeoTiffReader.readMultiband(rr, streaming = true)
    } yield {
      val crs = tiff.crs
      Projected(
        MultiPolygon(tiff.extent.reproject(crs, WebMercator).toPolygon()),
        3857)
    }
  }

  /** Work around bug in GeoTiff.crop(extent) method */
  def cropGeoTiff[T <: CellGrid](tiff: GeoTiff[T],
                                 extent: Extent): Option[Raster[T]] = {
    if (extent.intersects(tiff.extent)) {
      val bounds = tiff.rasterExtent.gridBoundsFor(extent)
      val clipExtent = tiff.rasterExtent.extentFor(bounds)
      val clip = tiff.crop(List(bounds)).next._2
      Some(Raster(clip, clipExtent))
    } else None
  }

  def geoTiffHistogram(tiff: GeoTiff[MultibandTile],
                       buckets: Int = 80,
                       size: Int = 128): Array[StreamingHistogram] = {
    def diagonal(tiff: GeoTiff[MultibandTile]): Int =
      math.sqrt(tiff.cols * tiff.cols + tiff.rows * tiff.rows).toInt

    val goldyLocksOverviews = tiff.overviews.filter { tiff =>
      val d = diagonal(tiff)
      (d >= size && d <= size * 4)
    }

    if (goldyLocksOverviews.nonEmpty) {
      // case: overview that is close enough to the size, not more than 4x larger
      // -- read the overview and get histogram
      val theOne = goldyLocksOverviews.minBy(diagonal)
      val hists = Array.fill(tiff.bandCount)(new StreamingHistogram(buckets))
      theOne.tile.foreachDouble { (band, v) =>
        if (!v.isNaN) {
          hists(band).countItem(v, 1)
        }
      }
      hists
    } else {
      // case: such oveview can't be found
      // -- take min overview and sample window from center
      val theOne = tiff.overviews.minBy(diagonal)
      val sampleBounds = {
        val side = math.sqrt(size * size / 2)
        val centerCol = theOne.cols / 2
        val centerRow = theOne.rows / 2
        GridBounds(
          colMin = math.max(0, centerCol - (side / 2)).toInt,
          rowMin = math.max(0, centerRow - (side / 2)).toInt,
          colMax = math.min(theOne.cols - 1, centerCol + (side / 2)).toInt,
          rowMax = math.min(theOne.rows - 1, centerRow + (side / 2)).toInt
        )
      }
      val sample = theOne.crop(List(sampleBounds)).next._2
      val hists = Array.fill(tiff.bandCount)(new StreamingHistogram(buckets))
      sample.foreachDouble { (band, v) =>
        if (!v.isNaN) {
          hists(band).countItem(v, 1)
        }
      }
      hists
    }
  }

  def geoTiffDoubleHistogram(tiff: GeoTiff[MultibandTile],
                             buckets: Int = 80,
                             size: Int = 128): Array[Histogram[Double]] = {

    def diagonal(tiff: GeoTiff[MultibandTile]): Int =
      math.sqrt(tiff.cols * tiff.cols + tiff.rows * tiff.rows).toInt

    val goldyLocksOverviews = tiff.overviews.filter { tiff =>
      val d = diagonal(tiff)
      (d >= size && d <= size * 4)
    }

    if (goldyLocksOverviews.nonEmpty) {
      // case: overview that is close enough to the size, not more than 4x larger
      // -- read the overview and get histogram
      val theOne = goldyLocksOverviews.minBy(diagonal)
      val hists = Array.fill(tiff.bandCount)(DoubleHistogram(buckets))
      theOne.tile.foreachDouble { (band, v) =>
        if (!v.isNaN) {
          hists(band).countItem(v, 1)
        }
      }
      hists.toArray
    } else {
      // case: such oveview can't be found
      // -- take min overview and sample window from center
      val theOne = tiff.overviews.minBy(diagonal)
      val sampleBounds = {
        val side = math.sqrt(size * size / 2)
        val centerCol = theOne.cols / 2
        val centerRow = theOne.rows / 2
        GridBounds(
          colMin = math.max(0, centerCol - (side / 2)).toInt,
          rowMin = math.max(0, centerRow - (side / 2)).toInt,
          colMax = math.min(theOne.cols - 1, centerCol + (side / 2)).toInt,
          rowMax = math.min(theOne.rows - 1, centerRow + (side / 2)).toInt
        )
      }
      val sample = theOne.crop(List(sampleBounds)).next._2
      val hists = Array.fill(tiff.bandCount)(DoubleHistogram(buckets))
      sample.foreachDouble { (band, v) =>
        if (!v.isNaN) {
          hists(band).countItem(v, 1)
        }
      }
      hists.toArray
    }
  }
}
