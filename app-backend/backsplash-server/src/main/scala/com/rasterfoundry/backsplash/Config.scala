package com.rasterfoundry.backsplash.server

import com.typesafe.config.ConfigFactory

object Config {
  private val config = ConfigFactory.load()

  object parallelism {
    private val parallelismConfig = config.getConfig("parallelism")
    val blazeConnectorPoolSize =
      parallelismConfig.getInt("blazeConnectorPoolSize")
  }

  object server {
    private val serverConfig = config.getConfig("server")
    val timeoutSeconds = serverConfig.getInt("timeoutSeconds")
    val requestLimit = serverConfig.getInt("requestLimit")
  }

  object cache {
    private val cacheConfig = config.getConfig("cache")
    val authorizationCacheEnable =
      cacheConfig.getBoolean("authorizationCacheEnable")
    val authenticationCacheEnable =
      cacheConfig.getBoolean("authenticationCacheEnable")
  }

  object metrics {
    private val metricsConfig = config.getConfig("metrics")
    val enableMetrics = metricsConfig.getBoolean("enableMetrics")
  }

  object healthcheck {
    private val healthcheckConfig = config.getConfig("healthcheck")
    val tiffBucket = healthcheckConfig.getString("tiffBucket")
    val tiffKey = healthcheckConfig.getString("tiffKey")
  }
}
