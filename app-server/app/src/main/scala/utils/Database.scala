package com.azavea.rf.utils

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import com.azavea.rf.datamodel.driver.ExtendedPostgresDriver

/**
  * PostGIS Database for Raster Foundry data
  *
  * @param jdbcUrl connection url for JDBC
  * @param dbUser user to authenticate in the database as
  * @param dbPassword password to use to authenticate user
  */
class Database(jdbcUrl: String, dbUser: String, dbPassword: String) {

  private val hikariConfig = new HikariConfig()
  // This property is now being manually set because it seemed not
  // to get picked up automatically through the JDBC url. See the hikari documentation
  // under the jdbcUrl for more information.
  hikariConfig.setDriverClassName("org.postgresql.Driver")
  hikariConfig.setJdbcUrl(jdbcUrl)
  hikariConfig.setUsername(dbUser)
  hikariConfig.setPassword(dbPassword)
  hikariConfig.setConnectionTimeout(2000)

  private val dataSource = new HikariDataSource(hikariConfig)

  /**
    * Utility function for closing the underlying DB connection pool
    */
  def closeConnectionPool() = dataSource.close

  // Custom driver to handle postgres column types
  val driver = ExtendedPostgresDriver
  import driver.api._
  val db = Database.forDataSource(dataSource)
  db.createSession()

}
