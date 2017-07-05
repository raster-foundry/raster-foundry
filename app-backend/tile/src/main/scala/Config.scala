package com.azavea.rf.tile

import com.typesafe.config.ConfigFactory

trait Config {
  val config = ConfigFactory.load().getConfig("tile-server")

  private lazy val httpConfig = config.getConfig("http")

  lazy val httpHost: String =
    httpConfig.getString("interface")

  lazy val httpPort: Int =
    httpConfig.getInt("port")

  lazy val defaultBucket: String =
    config.getString("bucket")

  lazy val withCaching: Boolean =
    config.getBoolean("cache.enabled")
}
