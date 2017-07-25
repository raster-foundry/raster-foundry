package com.azavea.rf.database

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

/**
  * PostGIS Database for Raster Foundry data
  *
  * @param jdbcUrl    connection url for JDBC
  * @param dbUser     user to authenticate in the database as
  * @param dbPassword password to use to authenticate user
  */
class Database(jdbcUrl: String, dbUser: String, dbPassword: String, slickThreadCount: Int, slickQueueSize: Int) {
  private val hikariConfig = new HikariConfig()
  // This property is now being manually set because it seemed not
  // to get picked up automatically through the JDBC url. See the hikari documentation
  // under the jdbcUrl for more information.
  hikariConfig.setDriverClassName("org.postgresql.Driver")
  hikariConfig.setJdbcUrl(jdbcUrl)
  hikariConfig.setUsername(dbUser)
  hikariConfig.setPassword(dbPassword)
  hikariConfig.setConnectionTimeout(1000)
  hikariConfig.setValidationTimeout(1000)
  hikariConfig.setIdleTimeout(600000)
  hikariConfig.setMaxLifetime(1800000)
  hikariConfig.setLeakDetectionThreshold(0)
  hikariConfig.setMaximumPoolSize(slickThreadCount * 5)
  hikariConfig.setMinimumIdle(slickThreadCount)

  private val dataSource = new HikariDataSource(hikariConfig)

  /**
    * Utility function for closing the underlying DB connection pool
    */
  def closeConnectionPool() = dataSource.close()

  // Custom driver to handle postgres column types
  val driver = ExtendedPostgresDriver

  import driver.api._

  val db = {
    val executor = AsyncExecutor("slick", numThreads = slickThreadCount, queueSize = slickQueueSize)
    driver.api.Database.forDataSource(dataSource, executor)
  }
}

object Database extends Config {
  def DEFAULT = new Database(jdbcUrl, dbUser, dbPassword, slickThreadCount, slickQueueSize)
}
