package com.azavea.rf.tile

import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit

trait Config {
  val config = ConfigFactory.load().getConfig("tile-server")

  private lazy val httpConfig = config.getConfig("http")

  lazy val httpHost: String =
    httpConfig.getString("interface")

  lazy val httpPort: Int =
    httpConfig.getInt("port")

  lazy val defaultBucket: String =
    config.getString("bucket")
}
