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
    val healthcheckTimeoutSeconds =
      serverConfig.getInt("healthcheckTimeoutSeconds")
    val doAccessLogging = serverConfig.getBoolean("doAccessLogging")
  }

  object cache {
    private val cacheConfig = config.getConfig("cache")
    val authorizationCacheEnable =
      cacheConfig.getBoolean("authorizationCacheEnable")
    val authenticationCacheEnable =
      cacheConfig.getBoolean("authenticationCacheEnable")
  }

  object healthcheck {
    private val healthcheckConfig = config.getConfig("healthcheck")
    val tiffBucket = healthcheckConfig.getString("tiffBucket")
    val tiffKey = healthcheckConfig.getString("tiffKey")
  }
}
