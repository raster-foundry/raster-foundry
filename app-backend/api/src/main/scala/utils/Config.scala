package com.azavea.rf.api.utils

import com.typesafe.config.ConfigFactory

trait Config {
  val config = ConfigFactory.load()
  private val httpConfig = config.getConfig("http")
  private val auth0Config = config.getConfig("auth0")
  private val rollbarConfig = config.getConfig("rollbar")
  private val featureFlagConfig = config.getConfig("featureFlags")
  private val s3Config = config.getConfig("s3")

  val httpHost = httpConfig.getString("interface")
  val httpPort = httpConfig.getInt("port")

  val auth0Domain = auth0Config.getString("domain")
  val auth0Bearer = auth0Config.getString("bearer")
  val auth0Secret = auth0Config.getString("secret")
  val auth0ClientId = auth0Config.getString("clientId")
  val auth0ManagementClientId = auth0Config.getString("managementClientId")
  val auth0ManagementSecret = auth0Config.getString("managementSecret")

  val rollbarClientToken = rollbarConfig.getString("clientToken")

  val featureFlags = featureFlagConfig.getConfigList("features")

  val thumbnailBucket = s3Config.getString("thumbnailBucket")
}
