package com.rasterfoundry.database.util

import cats.effect._
import cats.implicits._
import doobie.util.ExecutionContexts
import doobie.hikari.HikariTransactor
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

import scala.concurrent.ExecutionContext
import scala.util.Properties
import java.util.concurrent.Executors

import com.google.common.util.concurrent.ThreadFactoryBuilder

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

  val hikariConfig = new HikariConfig()
  hikariConfig.setPoolName("Raster-Foundry-Hikari-Pool")
  hikariConfig.setMaximumPoolSize(dbMaximumPoolSize)
  hikariConfig.setConnectionInitSql(
    s"SET statement_timeout = ${dbStatementTimeout};")
  hikariConfig.setJdbcUrl(jdbcUrl)
  hikariConfig.setUsername(dbUser)
  hikariConfig.setPassword(dbPassword)
  hikariConfig.setDriverClassName(jdbcDriver)

  val hikariDS = new HikariDataSource(hikariConfig)

  // Execution contexts to be used by Hikari
  val connectionEC =
    ExecutionContext.fromExecutor(
      ExecutionContext.fromExecutor(
        Executors.newFixedThreadPool(
          Properties.envOrElse("HIKARI_CONNECTION_THREADS", "8").toInt,
          new ThreadFactoryBuilder().setNameFormat("db-connection-%d").build()
        )
      ))

  val transactionEC =
    ExecutionContext.fromExecutor(
      ExecutionContext.fromExecutor(
        Executors.newCachedThreadPool(
          new ThreadFactoryBuilder().setNameFormat("db-transaction-%d").build()
        )
      ))

  def transactor(contextShift: ContextShift[IO]): HikariTransactor[IO] = {
    implicit val cs = contextShift
    HikariTransactor.apply[IO](hikariDS, connectionEC, transactionEC)
  }

  lazy val xaResource: Resource[IO, HikariTransactor[IO]] = {

    implicit val cs: ContextShift[IO] = IO.contextShift(
      ExecutionContext.fromExecutor(
        Executors.newFixedThreadPool(
          8,
          new ThreadFactoryBuilder().setNameFormat("db-client-%d").build()
        )
      ))

    HikariTransactor.newHikariTransactor[IO](
      jdbcDriver,
      jdbcNoDBUrl,
      dbUser,
      dbPassword,
      connectionEC,
      transactionEC
    )
  }

  // Only use in Tile subproject!!
  lazy val xa: HikariTransactor[IO] = {

    implicit val cs: ContextShift[IO] = IO.contextShift(
      ExecutionContext.fromExecutor(
        Executors.newFixedThreadPool(
          8,
          new ThreadFactoryBuilder().setNameFormat("db-client-%d").build()
        )
      ))

    HikariTransactor.apply[IO](
      hikariDS,
      connectionEC,
      transactionEC
    )
  }
}
