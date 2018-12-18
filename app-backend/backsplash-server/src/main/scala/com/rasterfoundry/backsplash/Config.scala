package com.rasterfoundry.backsplash.server

import com.typesafe.config.ConfigFactory

object Config {
  private val config = ConfigFactory.load()

  object parallelism {
    private val parallelismConfig = config.getConfig("parallelism")
    val threadPoolSize = parallelismConfig.getInt("threadPoolSize")
  }

  object server {
    private val serverConfig = config.getConfig("server")
    val timeoutSeconds = serverConfig.getInt("timeoutSeconds")
  }
}
