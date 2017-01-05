package com.azavea.rf.utils

import com.typesafe.config.ConfigFactory

trait Config {
  val config = ConfigFactory.load()
  private val httpConfig = config.getConfig("http")
  private val auth0Config = config.getConfig("auth0")
  private val featureFlagConfig = config.getConfig("featureFlags")

  val httpHost = httpConfig.getString("interface")
  val httpPort = httpConfig.getInt("port")

  val auth0Domain = auth0Config.getString("domain")
  val auth0Bearer = auth0Config.getString("bearer")
  val auth0Secret = java.util.Base64.getUrlDecoder.decode(auth0Config.getString("secret"))
  val auth0ClientId = auth0Config.getString("clientId")

  val featureFlags = featureFlagConfig.getConfigList("features")
}
