package com.azavea.rf.common

import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit

object Config {
  private val config = ConfigFactory.load()

  object airflow {
    private val airflowConfig = config.getConfig("airflow")

    val baseUrl = airflowConfig.getString("baseURL")

  }

  object memcached {
    private lazy val memcachedConfig = config.getConfig("memcached")

    lazy val host: String =
      memcachedConfig.getString("host")

    lazy val port: Int =
      memcachedConfig.getInt("port")

    lazy val threads: Int =
      memcachedConfig.getInt("threads")

    lazy val ttl: FiniteDuration =
      FiniteDuration(memcachedConfig.getDuration("ttl").toNanos, TimeUnit.NANOSECONDS)

    lazy val heapEntryTTL: FiniteDuration =
      FiniteDuration(memcachedConfig.getDuration("heap.ttl").toNanos, TimeUnit.NANOSECONDS)

    lazy val heapMaxEntries: Int =
      memcachedConfig.getInt("heap.max-entries")
  }
}