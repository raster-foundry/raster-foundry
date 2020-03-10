package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.util.RFTransactor

import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.typesafe.config.ConfigFactory
import doobie._
import doobie.free.connection.unit
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Strategy
import org.flywaydb.core.Flyway
import org.scalatest.{BeforeAndAfterAll, Suite}

import scala.concurrent.ExecutionContext

import java.util.UUID
import java.util.concurrent.Executors

// SetupTemplateDB is a singleton that is used to instantiate the template db
// once and only once per test run
object SetupTemplateDB {
  private val config = ConfigFactory.load()
  private val migrations = config.getConfig("migrations")
  val migrationsHome = migrations.getString("migrationsHome")
  val templateDbName = "testing_template";

  // db create/drop cannot be done with transactions
  // this transactor has error handling and cleanup but no transaction/auto-commit behavior
  val xantConfig = RFTransactor.TransactorConfig(dbName = "")

  implicit val contextShift: ContextShift[IO] =
    IO.contextShift(
      ExecutionContext.fromExecutor(
        Executors.newCachedThreadPool(
          new ThreadFactoryBuilder().setNameFormat("db-transactor-%d").build()
        )
      )
    )

  val xant = RFTransactor.nonHikariTransactor(xantConfig)

  // we use a template database so that migrations only need to be run once
  // test-suite specific databases are created using this db as a template

  def withoutTransaction[A](p: ConnectionIO[A]): ConnectionIO[A] =
    FC.setAutoCommit(true) *> p <* FC.setAutoCommit(false)

  // drop and create template database
  val setupDB =
    (fr"DROP DATABASE IF EXISTS" ++ Fragment.const(templateDbName) ++ fr";" ++
      fr"CREATE DATABASE" ++ Fragment.const(templateDbName)).update.run

  withoutTransaction(setupDB)
    .transact(xant)
    .unsafeRunSync

  val jdbcUrl = s"${xantConfig.postgresUrl}${templateDbName}"

  val flyway = Flyway
    .configure()
    .dataSource(jdbcUrl, xantConfig.user, xantConfig.password)
    .locations(migrationsHome)
    .load()
  flyway.migrate()

}

trait DBTestConfig extends BeforeAndAfterAll { this: Suite =>
  SetupTemplateDB
  val dbName = getClass.getSimpleName.toLowerCase
  val templateDbName = SetupTemplateDB.templateDbName

  // Transactor used by tests with rollback behavior and transactions
  def xaConfig = RFTransactor.TransactorConfig(
    dbName = dbName,
    maximumPoolSize = 100
  )
  def xa =
    Transactor.after
      .set(RFTransactor.nonHikariTransactor(xaConfig), HC.rollback)

  // this transactor has error handling and cleanup but no transaction/auto-commit behavior
  val xantConfig = RFTransactor.TransactorConfig(dbName = "")

  implicit val contextShift: ContextShift[IO] =
    IO.contextShift(
      ExecutionContext.fromExecutor(
        Executors.newCachedThreadPool(
          new ThreadFactoryBuilder().setNameFormat("db-transactor-%d").build()
        )
      )
    )

  val xant = RFTransactor.nonHikariTransactor(xantConfig)
  Transactor.strategy
    .set(xant, Strategy.default.copy(before = unit, after = unit))

  def withoutTransaction[A](p: ConnectionIO[A]): ConnectionIO[A] =
    FC.setAutoCommit(true) *> p <* FC.setAutoCommit(false)

  override def beforeAll {
    // using the no-transaction transactor, drop the database in case it's hanging around for some reason
    // and then create the database
    val x = (
      fr"DROP DATABASE IF EXISTS" ++ Fragment.const(dbName) ++ fr";CREATE DATABASE" ++ Fragment
        .const(dbName) ++ fr"WITH TEMPLATE" ++ Fragment
        .const(templateDbName)
    ).update.run

    withoutTransaction(x).transact(xant).unsafeRunSync
    ()
  }

  override def afterAll {
    // using the no-transaction transactor, drop the db for the test suite
    val x = (fr"DROP DATABASE IF EXISTS" ++ Fragment.const(dbName)).update.run
    withoutTransaction(x)
      .transact(xant)
      .unsafeRunSync
    ()
  }

  val defaultUserQ = UserDao.unsafeGetUserById("default")
  val rootOrgQ = OrganizationDao.query
    .filter(UUID.fromString("9e2bef18-3f46-426b-a5bd-9913ee1ff840"))
    .selectQ
    .unique
  val changeDetectionProjQ = ProjectDao.query
    .filter(fr"id = ${UUID.fromString("30fd336a-d360-4c9f-9f99-bb7ac4b372c4")}")
    .selectQ
    .unique
}
