package com.rasterfoundry.http4s

import com.typesafe.config.ConfigFactory

object Config {
  private val config = ConfigFactory.load()

  object cache {
    private val cacheConfig = config.getConfig("cache")
    val authenticationCacheEnable =
      cacheConfig.getBoolean("authenticationCacheEnable")
  }
}
