package com.azavea.rf.database.util

import cats.effect.IO
import doobie.hikari.HikariTransactor

import scala.util.Properties

trait Config {
  var jdbcDriver: String = "org.postgresql.Driver"
  val jdbcNoDBUrl: String =
    Properties.envOrElse(
      "POSTGRES_URL",
      "jdbc:postgresql://database.service.rasterfoundry.internal/")
  val jdbcDBName: String =
    Properties.envOrElse("POSTGRES_NAME", "rasterfoundry")
  val jdbcUrl: String = jdbcNoDBUrl + jdbcDBName
  val dbUser: String = Properties.envOrElse("POSTGRES_USER", "rasterfoundry")
  val dbPassword: String =
    Properties.envOrElse("POSTGRES_PASSWORD", "rasterfoundry")
  val dbStatementTimeout: String =
    Properties.envOrElse("POSTGRES_STATEMENT_TIMEOUT", "30000")
  val dbMaximumPoolSize: Int =
    Properties.envOrElse("POSTGRES_DB_POOL_SIZE", "5").toInt
}

object RFTransactor extends Config {

  implicit lazy val xa: HikariTransactor[IO] = (for {
    xa <- HikariTransactor.newHikariTransactor[IO](
      driverClassName = jdbcDriver,
      url = jdbcUrl,
      user = dbUser,
      pass = dbPassword
    )
    _ <- xa.configure(c =>
      IO {
        c.setPoolName("Raster-Foundry-Hikari-Pool")
        c.setMaximumPoolSize(dbMaximumPoolSize)
        c.setConnectionInitSql(
          s"SET statement_timeout = ${dbStatementTimeout};")
    })
  } yield xa).unsafeRunSync()

}
