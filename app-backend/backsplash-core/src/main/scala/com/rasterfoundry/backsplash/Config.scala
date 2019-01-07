package com.rasterfoundry.backsplash
import com.typesafe.config.ConfigFactory

object Config {

  private val config = ConfigFactory.load()

  object cache {
    private val cacheConfig = config.getConfig("cache")
    val histogramCacheEnable =
      cacheConfig.getBoolean("core.histogramCacheEnable")
    val tileCacheEnable = cacheConfig.getBoolean("core.tileCacheEnable")
    val rasterSourceCacheEnable =
      cacheConfig.getBoolean("core.rasterSourceCacheEnable")
  }
}
