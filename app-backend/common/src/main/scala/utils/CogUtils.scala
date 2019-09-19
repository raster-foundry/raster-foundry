package com.rasterfoundry.common.utils

import com.rasterfoundry.common.cache._
import com.rasterfoundry.common.cache.kryo._
import com.rasterfoundry.common.{Config => CommonConfig}

import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.histogram._
import geotrellis.contrib.vlm.gdal.GDALRasterSource
import geotrellis.proj4._
import geotrellis.vector.Projected

import cats.implicits._

import com.typesafe.scalalogging.LazyLogging

import java.net.URLDecoder

object CogUtils extends LazyLogging {
  lazy val cacheConfig = CommonConfig.memcached
  lazy val memcachedClient = KryoMemcachedClient.default
  lazy val rfCache = new CacheClient(memcachedClient)

  def getTiffExtent(uri: String): Projected[MultiPolygon] = {
    val rasterSource = GDALRasterSource(URLDecoder.decode(uri, "UTF-8"))
    val crs = rasterSource.crs
    Projected(
      MultiPolygon(rasterSource.extent.reproject(crs, WebMercator).toPolygon()),
      3857)
  }

  def histogramFromUri(uri: String,
                       buckets: Int = 80): Option[Array[Histogram[Double]]] = {
    // use the resolution that's closest to 100,000 pixels and not greater than 400,000 pixels
    // this must fit in a request/response cycle. A 500x500 overview is reasonable, and that adds
    // up to 250,000 pixels
    // We may need to adjust this number depending on how fast our API is able to process it, these
    // numbers are based off local testing
    val rasterSource = GDALRasterSource(URLDecoder.decode(uri, "UTF-8"))
    rasterSource.resolutions
      .filter(r => r.rows * r.cols < 400000)
      .toNel
      .flatMap(
        resNel =>
          rasterSource
            .resampleToGrid(
              resNel.toList.minBy(r => scala.math.abs(100000 - r.rows * r.cols))
            )
            .read()
            .map(_.tile.histogramDouble(buckets)))
  }
}
