package com.azavea.rf.tile

import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit

trait Config {
  val config = ConfigFactory.load()
  private lazy val httpConfig = config.getConfig("http")

  lazy val httpHost = httpConfig.getString("interface")
  lazy val httpPort = httpConfig.getInt("port")

  private lazy val tileserverConfig = config.getConfig("tile-server")
  private lazy val cacheConfig = tileserverConfig.getConfig("cache")

  lazy val cacheExpiration: Duration =
    if (cacheConfig.hasPath("expiration"))
      FiniteDuration(cacheConfig.getDuration("expiration").toNanos, TimeUnit.NANOSECONDS)
    else
      5.minutes

  lazy val cacheSize: Long =
    if (cacheConfig.hasPath("size")) cacheConfig.getLong("size") else 512l

  lazy val defaultBucket:String =
    tileserverConfig.getString("bucket")
}
