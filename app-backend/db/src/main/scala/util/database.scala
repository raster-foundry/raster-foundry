package com.rasterfoundry.database.util

import cats.effect._
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import doobie.hikari.HikariTransactor
import doobie.util.transactor.Transactor

import scala.concurrent.ExecutionContext
import scala.util.Properties

import java.util.concurrent.Executors

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
      maximumPoolSize: Int =
        Properties.envOrElse("POSTGRES_DB_POOL_SIZE", "32").toInt,
      poolName: String = "Raster-Foundry-Hikari-Pool"
  ) {
    val url = postgresUrl ++ dbName

    lazy val hikariDataSource = {
      val hikariConfig = new HikariConfig()
      hikariConfig.setPoolName(poolName)
      hikariConfig.setMaximumPoolSize(maximumPoolSize)
      hikariConfig.setJdbcUrl(url)
      hikariConfig.setUsername(user)
      hikariConfig.setPassword(password)
      hikariConfig.setDriverClassName(driver)
      hikariConfig.setLeakDetectionThreshold(30 * 1000)
      hikariConfig.setAutoCommit(false)
      new HikariDataSource(hikariConfig)
    }

    val connectionEC: ExecutionContext =
      ExecutionContext.fromExecutor(
        Executors.newFixedThreadPool(
          Properties.envOrElse("HIKARI_CONNECTION_THREADS", "8").toInt,
          new ThreadFactoryBuilder().setNameFormat("db-connection-%d").build()
        )
      )

    val transactionEC: ExecutionContext =
      ExecutionContext.fromExecutor(
        Executors.newCachedThreadPool(
          new ThreadFactoryBuilder().setNameFormat("db-transaction-%d").build()
        )
      )
  }

  def buildTransactor(
      config: TransactorConfig = TransactorConfig()
  )(implicit cs: ContextShift[IO]): HikariTransactor[IO] = {
    HikariTransactor.apply[IO](
      config.hikariDataSource,
      config.connectionEC,
      config.transactionEC
    )
  }

  def buildTransactorResource(
      config: TransactorConfig = TransactorConfig()
  )(implicit cs: ContextShift[IO]): Resource[IO, HikariTransactor[IO]] = {
    HikariTransactor.newHikariTransactor[IO](
      config.driver,
      config.postgresUrl,
      config.user,
      config.password,
      config.connectionEC,
      config.transactionEC
    )
  }

  def nonHikariTransactor(config: TransactorConfig)(
      implicit cs: ContextShift[IO]) = {
    Transactor.fromDriverManager[IO](
      "org.postgresql.Driver",
      config.url,
      config.user,
      config.password
    )
  }

  def xaResource(
      implicit cs: ContextShift[IO]): Resource[IO, HikariTransactor[IO]] =
    buildTransactorResource()
}
