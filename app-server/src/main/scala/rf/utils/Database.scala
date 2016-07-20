package com.azavea.rf.utils

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

/**
  * PostGIS Database for Raster Foundry data
  * 
  * @param jdbcUrl connection url for JDBC
  * @param dbUser user to authenticate in the database as
  * @param dbPassword password to use to authenticate user
  */
class Database(jdbcUrl: String, dbUser: String, dbPassword: String) {

  private val hikariConfig = new HikariConfig()
  hikariConfig.setJdbcUrl(jdbcUrl)
  hikariConfig.setUsername(dbUser)
  hikariConfig.setPassword(dbPassword)
  hikariConfig.setConnectionTimeout(2000)
  
  private val dataSource = new HikariDataSource(hikariConfig)

  val driver = slick.driver.PostgresDriver
  import driver.api._
  val db = Database.forDataSource(dataSource)
  db.createSession()
}
