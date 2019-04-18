package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.util.RFTransactor

import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Strategy
import doobie.free.connection.unit
import cats.effect._
import cats.implicits._
import org.scalatest.{Suite, BeforeAndAfterAll}
import scala.sys.process._
import scala.concurrent.ExecutionContext
import com.google.common.util.concurrent.ThreadFactoryBuilder

import java.util.concurrent.Executors
import java.util.UUID

// SetupTemplateDB is a singleton that is used to instantiate the template db
// once and only once per test run
object SetupTemplateDB {
  val templateDbName = "testing_template";

  // db create/drop cannot be done with transactions
  // this transactor has error handling and cleanup but no transaction/auto-commit behavior
  val xantConfig = RFTransactor.TransactorConfig(dbName = "")
  val xant = RFTransactor.buildTransactorResource(xantConfig) map { xa =>
    Transactor.strategy.set(
      xa,
      Strategy.default.copy(before = unit, after = unit)
    )
  }

  // we use a template database so that migrations only need to be run once
  // test-suite specific databases are created using this db as a template

  // drop and create template database
  xant.use { t =>
    (
      fr"DROP DATABASE IF EXISTS" ++ Fragment.const(templateDbName) ++ fr";" ++
        fr"CREATE DATABASE" ++ Fragment.const(templateDbName)
    ).update.run.transact(t)
  }.unsafeRunSync

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
  val templateDbName = SetupTemplateDB.templateDbName

  // Transactor used by tests with rollback behavior and transactions
  def xaConfig = RFTransactor.TransactorConfig(
    dbName = dbName,
    maximumPoolSize = 100
  )
  def xa = RFTransactor.buildTransactorResource(xaConfig) map { xa =>
    Transactor.after.set(xa, HC.rollback)
  }

  // this transactor has error handling and cleanup but no transaction/auto-commit behavior
  val xantConfig = RFTransactor.TransactorConfig(dbName = "")
  val xant = RFTransactor.buildTransactorResource(xantConfig) map { xa =>
    Transactor.strategy.set(
      xa,
      Strategy.default
        .copy(before = unit, after = unit)
    )
  }

  override def beforeAll {
    // using the no-transaction transactor, drop the database in case it's hanging around for some reason
    // and then create the database
    xant.use { t =>
      (
        fr"DROP DATABASE IF EXISTS" ++ Fragment.const(dbName) ++ fr";CREATE DATABASE" ++ Fragment
          .const(dbName) ++ fr"WITH TEMPLATE" ++ Fragment
          .const(templateDbName)
      ).update.run.transact(t)
    }.unsafeRunSync
  }

  override def afterAll {
    // using the no-transaction transactor, drop the db for the test suite
    xant.use { t =>
      (fr"DROP DATABASE IF EXISTS" ++ Fragment.const(dbName)).update.run
        .transact(t)
    }.unsafeRunSync
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
