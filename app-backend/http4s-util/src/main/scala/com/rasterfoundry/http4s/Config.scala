package com.rasterfoundry.http4s

import com.amazonaws.xray.plugins.{EC2Plugin, ECSPlugin}
import com.typesafe.config.ConfigFactory
import scala.collection.JavaConverters._
import com.rasterfoundry.common.{Config => CommonConfig}

object Config {
  private val config = ConfigFactory.load()

  val environment = CommonConfig.awsbatch.environment

  object cache {
    private val cacheConfig = config.getConfig("cache")
    val authenticationCacheEnable =
      cacheConfig.getBoolean("authenticationCacheEnable")
  }

  object ecs {
    // Used to annotate traces in AWS XRay
    val ecsInstance = new ECSPlugin().getRuntimeContext.asScala.mapValues {
      case value: String => value
      case _             => ""
    }
  }

  object ec2 {
    // Used to annotate traces in AWS XRay
    val ec2Data = new EC2Plugin().getRuntimeContext.asScala.mapValues {
      case value: String => value
      case _             => ""
    }
  }

}
