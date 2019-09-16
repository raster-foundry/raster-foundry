package com.rasterfoundry.backsplash.server

import com.typesafe.config.ConfigFactory

import java.util.UUID

object Config {
  private val config = ConfigFactory.load()

  object publicData {
    private val publicDataConfig = config.getConfig("publicData")
    val landsat8DatasourceId =
      UUID.fromString(publicDataConfig.getString("landsat8DatasourceId"))
    val sentinel2DatasourceId =
      UUID.fromString(publicDataConfig.getString("sentinel2DatasourceId"))
    val enableMultiTiff =
      publicDataConfig.getBoolean("enableMultiTiff")
  }

  object parallelism {
    private val parallelismConfig = config.getConfig("parallelism")
    val dbThreadPoolSize = parallelismConfig.getInt("dbThreadPoolSize")
    val http4sThreadPoolSize = parallelismConfig.getInt("http4sThreadPoolSize")
    val blazeThreadPoolSize = parallelismConfig.getInt("blazeThreadPoolSize")
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
