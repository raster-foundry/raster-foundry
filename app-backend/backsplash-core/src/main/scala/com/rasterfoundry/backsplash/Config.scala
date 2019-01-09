package com.rasterfoundry.backsplash
import com.typesafe.config.ConfigFactory
import net.spy.memcached.ClientMode

object Config {

  private val config = ConfigFactory.load()

  object RasterSource {
    private val rasterSourceConfig = config.getConfig("rasterSource")
    val enableGDAL = rasterSourceConfig.getBoolean("enableGDAL")
  }

  object cache {
    private val cacheConfig = config.getConfig("cache")
    val histogramCacheEnable =
      cacheConfig.getBoolean("core.histogramCacheEnable")
    val tileCacheEnable = cacheConfig.getBoolean("core.tileCacheEnable")
    val rasterSourceCacheEnable =
      cacheConfig.getBoolean("core.rasterSourceCacheEnable")

    val memcachedHost = cacheConfig.getString("core.memcachedHost")
    val memcachedPort = cacheConfig.getInt("core.memcachedPort")

    val memcachedClientMode =
      if (cacheConfig.getBoolean("core.memcachedDynamicClientMode")) {
        ClientMode.Dynamic
      } else {
        ClientMode.Static
      }

    val memcachedTimeout = cacheConfig.getInt("core.memcachedTimeout")

  }
}
