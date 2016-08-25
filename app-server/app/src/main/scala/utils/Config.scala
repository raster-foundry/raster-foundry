package com.azavea.rf.utils

import com.typesafe.config.ConfigFactory


trait Config {
  private val config = ConfigFactory.load()
  private val httpConfig = config.getConfig("http")
  private val databaseConfig = config.getConfig("slick.db")
  private val auth0Config = config.getConfig("auth0")

  val httpHost = httpConfig.getString("interface")
  val httpPort = httpConfig.getInt("port")

  val jdbcUrl = databaseConfig.getString("url")
  val dbUser = databaseConfig.getString("user")
  val dbPassword = databaseConfig.getString("password")

  val auth0Secret = java.util.Base64.getUrlDecoder.decode(auth0Config.getString("secret"))
}
