package com.azavea.rf.database

import com.typesafe.config.ConfigFactory

trait Config {
  private val config = ConfigFactory.load()
  private val slickConfig = config.getConfig("slick")
  private val databaseConfig = config.getConfig("slick.db")
  val jdbcNoDBUrl = databaseConfig.getString("url")
  val jdbcDBName = databaseConfig.getString("name")
  val jdbcUrl = jdbcNoDBUrl + jdbcDBName
  val dbUser = databaseConfig.getString("user")
  val dbPassword = databaseConfig.getString("password")
  val slickThreadCount = slickConfig.getInt("threads")
  val slickQueueSize = slickConfig.getInt("queueSize")
}
