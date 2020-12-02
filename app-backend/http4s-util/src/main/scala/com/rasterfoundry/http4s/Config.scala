package com.rasterfoundry.http4s

import com.rasterfoundry.common.{Config => CommonConfig}

import com.amazonaws.xray.plugins.{EC2Plugin, ECSPlugin}
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._

object Config {
  private val config = ConfigFactory.load()

  val environment = CommonConfig.awsbatch.environment

  object xray {
    private val xrayConfig = config.getConfig("awsxray")
    val host = xrayConfig.getString("host")
  }

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
    val ec2Plugin = new EC2Plugin()
    val ec2Data = ec2Plugin.getRuntimeContext.asScala.mapValues {
      case value: String => value
      case _             => ""
    }
  }

}
