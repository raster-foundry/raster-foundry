package com.azavea.rf.utils

import com.typesafe.config.ConfigFactory


trait Config {
  val config = ConfigFactory.load()
  private val httpConfig = config.getConfig("http")
  private val databaseConfig = config.getConfig("slick.db")
  private val auth0Config = config.getConfig("auth0")

  val httpHost = httpConfig.getString("interface")
  val httpPort = httpConfig.getInt("port")

  val jdbcNoDBUrl = databaseConfig.getString("url")
  val jdbcDBName = databaseConfig.getString("name")
  val jdbcUrl = jdbcNoDBUrl + jdbcDBName
  val dbUser = databaseConfig.getString("user")
  val dbPassword = databaseConfig.getString("password")

  val auth0Secret = java.util.Base64.getUrlDecoder.decode(auth0Config.getString("secret"))
}
