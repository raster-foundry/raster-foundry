package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.util.RFTransactor

import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Strategy
import doobie.free.connection.unit
import cats.implicits._
import org.scalatest.{Suite, BeforeAndAfterAll}
import scala.sys.process._

import java.util.UUID

// SetupTemplateDB is a singleton that is used to instantiate the template db
// once and only once per test run
object SetupTemplateDB {
  val templateDbName = "testing_template";

  // db create/drop cannot be done with transactions
  // this transactor has error handling and cleanup but no transaction/auto-commit behavior
  val xantConfig = RFTransactor.TransactorConfig(dbName = "")
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

  // run migrations using sbt pointed to the template db
  Process(
    Seq("./sbt", ";mg init;mg update;mg apply"),
    None,
    "POSTGRES_URL" -> s"${xantConfig.postgresUrl}${templateDbName}"
  ).!
}

trait DBTestConfig extends BeforeAndAfterAll { this: Suite =>
  SetupTemplateDB
  val dbName = getClass.getSimpleName.toLowerCase
  println(s"DBNAME: ${dbName}")
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
