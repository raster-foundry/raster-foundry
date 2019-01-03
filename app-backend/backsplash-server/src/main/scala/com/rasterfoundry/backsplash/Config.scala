package com.rasterfoundry.backsplash.server

import com.typesafe.config.ConfigFactory

object Config {
  private val config = ConfigFactory.load()

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
    val graphiteUrl = serverConfig.getString("graphiteUrl")
  }

  object cache {
    private val cacheConfig = config.getConfig("cache")
    val authorizationCacheEnable =
      cacheConfig.getBoolean("authorizationCacheEnable")
    val authenticationCacheEnable =
      cacheConfig.getBoolean("authenticationCacheEnable")
  }
}
