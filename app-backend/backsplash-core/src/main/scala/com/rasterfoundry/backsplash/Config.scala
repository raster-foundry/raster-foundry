package com.rasterfoundry.backsplash

import com.typesafe.config.ConfigFactory

object Config {

  private val config = ConfigFactory.load()

  object RasterSource {
    private val rasterSourceConfig = config.getConfig("rasterSource")
    val enableGDAL = rasterSourceConfig.getBoolean("enableGDAL")
  }

  object parallelism {

    /**
      * Controls the max level of concurrent effects processed with [[fs2.Stream#parEvalMap]] in
      * [[MosaicImplicits]]
      **/
    private val parallelismConfig = config.getConfig("parallelism")
    val streamConcurrency = parallelismConfig.getInt("core.streamConcurrency")
  }

  object cache {
    private val cacheConfig = config.getConfig("cache")
    val histogramCacheEnable =
      cacheConfig.getBoolean("core.histogramCacheEnable")
  }
}
