package com.rasterfoundry.database.util

import cats.effect._
import doobie.hikari.HikariTransactor
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

import scala.concurrent.ExecutionContext
import scala.util.Properties
import java.util.concurrent.Executors

import com.google.common.util.concurrent.ThreadFactoryBuilder

object RFTransactor {
  final case class TransactorConfig(
      dbName: String = Properties.envOrElse("POSTGRES_NAME", "rasterfoundry"),
      driver: String = "org.postgresql.Driver",
      postgresUrl: String = Properties.envOrElse(
        "POSTGRES_URL",
        "jdbc:postgresql://database.service.rasterfoundry.internal/"
      ),
      user: String = Properties.envOrElse("POSTGRES_USER", "rasterfoundry"),
      password: String =
        Properties.envOrElse("POSTGRES_PASSWORD", "rasterfoundry"),
      statementTimeout: String =
        Properties.envOrElse("POSTGRES_STATEMENT_TIMEOUT", "30000"),
      maximumPoolSize: Int =
        Properties.envOrElse("POSTGRES_DB_POOL_SIZE", "5").toInt,
      poolName: String = "Raster-Foundry-Hikari-Pool",
      maybeInitSql: Option[String] = None,
      contextShift: ContextShift[IO] = IO.contextShift(
        ExecutionContext.fromExecutor(
          Executors.newFixedThreadPool(
            8,
            new ThreadFactoryBuilder().setNameFormat("db-client-%d").build()
          )
        )
      )
  ) {
    val url = postgresUrl + dbName
    val initSql =
      maybeInitSql.getOrElse(s"SET statement_timeout = ${statementTimeout};")

    lazy val hikariDataSource = {
      val hikariConfig = new HikariConfig()
      hikariConfig.setPoolName(poolName)
      hikariConfig.setMaximumPoolSize(maximumPoolSize)
      hikariConfig.setConnectionInitSql(initSql)
      hikariConfig.setJdbcUrl(url)
      hikariConfig.setUsername(user)
      hikariConfig.setPassword(password)
      hikariConfig.setDriverClassName(driver)

      new HikariDataSource(hikariConfig)
    }

    val connectionEC =
      ExecutionContext.fromExecutor(
        ExecutionContext.fromExecutor(
          Executors.newFixedThreadPool(
            Properties.envOrElse("HIKARI_CONNECTION_THREADS", "8").toInt,
            new ThreadFactoryBuilder().setNameFormat("db-connection-%d").build()
          )
        )
      )

    val transactionEC =
      ExecutionContext.fromExecutor(
        ExecutionContext.fromExecutor(
          Executors.newCachedThreadPool(
            new ThreadFactoryBuilder()
              .setNameFormat("db-transaction-%d")
              .build()
          )
        )
      )
  }

  def buildTransactor(
      config: TransactorConfig = TransactorConfig()
  ): HikariTransactor[IO] = {
    implicit val cs: ContextShift[IO] = config.contextShift
    HikariTransactor.apply[IO](
      config.hikariDataSource,
      config.connectionEC,
      config.transactionEC
    )
  }

  def buildTransactorResource(
      config: TransactorConfig = TransactorConfig()
  ): Resource[IO, HikariTransactor[IO]] = {
    implicit val cs: ContextShift[IO] = config.contextShift
    HikariTransactor.newHikariTransactor[IO](
      config.driver,
      config.postgresUrl,
      config.user,
      config.password,
      config.connectionEC,
      config.transactionEC
    )
  }

  lazy val xaResource: Resource[IO, HikariTransactor[IO]] =
    buildTransactorResource()
}
