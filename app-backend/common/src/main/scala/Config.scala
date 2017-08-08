package com.azavea.rf.common

import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory
import net.spy.memcached.ClientMode

import scala.concurrent.duration._

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

    lazy val clientMode: ClientMode =
      if (memcachedConfig.getBoolean("dynamicClientMode")) {
        ClientMode.Dynamic
      } else {
        ClientMode.Static
      }

    lazy val timeout: Long = memcachedConfig.getLong("timeout")

    lazy val threads: Int =
      memcachedConfig.getInt("threads")

    lazy val enabled: Boolean =
      memcachedConfig.getBoolean("enabled")

    object layerAttributes {
      lazy val enabled: Boolean =
        memcachedConfig.getBoolean("layerAttributes.enabled")
    }

    object layerTile {
      lazy val enabled: Boolean =
        memcachedConfig.getBoolean("layerTile.enabled")
    }

    object tool {
      lazy val enabled: Boolean =
        memcachedConfig.getBoolean("tool.enabled")
    }

  }

  object geotrellis {
    private lazy val geotrellisConfig = config.getConfig("geotrellis")

    lazy val postgresAttributeStoreThreads: Int = geotrellisConfig.getInt("attributeStore.postgres.threads")

    lazy val postgresAttributeStoreTimeout: FiniteDuration =
      FiniteDuration(geotrellisConfig.getDuration("attributeStore.postgres.timeout").toNanos, TimeUnit.NANOSECONDS)
  }

}
