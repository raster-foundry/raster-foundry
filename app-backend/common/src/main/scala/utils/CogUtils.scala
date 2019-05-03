package com.rasterfoundry.common.utils

import com.rasterfoundry.common.cache._
import com.rasterfoundry.common.cache.kryo._
import com.rasterfoundry.common.{Config => CommonConfig}
import com.rasterfoundry.datamodel.TiffWithMetadata

import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.histogram._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.io.geotiff.reader.{GeoTiffReader, TiffTagsReader}
import geotrellis.proj4._
import geotrellis.vector.Projected

import cats.data._
import cats.implicits._

import scala.concurrent._

object CogUtils {
  lazy val cacheConfig = CommonConfig.memcached
  lazy val memcachedClient = KryoMemcachedClient.default
  lazy val rfCache = new CacheClient(memcachedClient)

  /** Read GeoTiff from URI while caching the header bytes in memcache */
  def fromUri(uri: String)(
      implicit ec: ExecutionContext): OptionT[Future, TiffWithMetadata] = {
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
          TiffWithMetadata(GeoTiffReader.readMultiband(crr, streaming = true),
                           TiffTagsReader.read(crr))
        }
      }
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
