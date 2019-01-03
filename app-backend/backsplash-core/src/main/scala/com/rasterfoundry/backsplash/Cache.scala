package com.rasterfoundry.backsplash

import com.typesafe.scalalogging.LazyLogging
import geotrellis.raster.MultibandTile
import geotrellis.raster.histogram.Histogram
import scalacache._
import scalacache.caffeine._
import scalacache.memcached._
import scalacache.memoization._

object Cache extends LazyLogging {

  val tileCache: Cache[Option[MultibandTile]] = {

    implicit val cacheConfig = CacheConfig(
      memoization = MemoizationConfig(
        MethodCallToStringConverter.includeClassConstructorParams)
    )

    CaffeineCache[Option[MultibandTile]]
  }

  val tileCacheFlags =
    Flags(Config.cache.tileCacheEnable, Config.cache.tileCacheEnable)
  logger.info(s"Tile Cache Status: ${tileCacheFlags}")

  val histCache: Cache[Array[Histogram[Double]]] =
    CaffeineCache[Array[Histogram[Double]]]

  val histCacheFlags =
    Flags(Config.cache.histogramCacheEnable, Config.cache.histogramCacheEnable)
  logger.info(s"Histogram Cache Status: ${histCacheFlags}")

}
