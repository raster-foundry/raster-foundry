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

trait DBTestConfig extends BeforeAndAfterAll { this: Suite =>
  val dbName = getClass.getSimpleName.toLowerCase
  // transactor that will built for each test suite
  def xaConfig = RFTransactor.TransactorConfig(
    dbName = dbName
  )
  def xa = RFTransactor.buildTransactorResource(xaConfig) map { xa =>
    Transactor.after.set(xa, HC.rollback)
  }

  // db create/drop cannot be done with transactions
  // this transactor has only error handling and no transaction/auto-commit behavior
  val xantConfig = RFTransactor.TransactorConfig(dbName = "")
  val s = doobie.util.transactor.Strategy(
    unit,
    unit,
    RFTransactor.buildTransactor().strategy.oops,
    FC.unit
  )
  val xant = RFTransactor.buildTransactorResource(xantConfig) map { xa =>
    Transactor.strategy.set(
      xa,
      Strategy.default
        .copy(before = unit, after = unit, always = unit, oops = unit)
    )
  }

  // setup template database and run migrations

  override def beforeAll {
    println(dbName)
    // drop the database in case it's hanging around for some reason
    xant.use { t =>
      (fr"DROP DATABASE IF EXISTS" ++ Fragment.const(dbName)).update.run
        .transact(t)
    }.unsafeRunSync

    xant.use { t =>
      // create the database for the test suite using the bare transactor
      (fr"CREATE DATABASE" ++ Fragment.const(dbName)).update.run
        .transact(t)
    }.unsafeRunSync

    // using the suite specific config, run migrations
    // % `./sbt` ("mg init", s"POSTGRES_URL = ${url}")
    Process(
      Seq("./sbt", ";mg init;mg update;mg apply"),
      None,
      "POSTGRES_URL" -> xaConfig.url
    ).!
    // Process(
    //   Seq("./sbt", "mg update"),
    //   None,
    //   "POSTGRES_URL" -> xaConfig.url
    // ).!
    // Process(
    //   Seq("./sbt", "mg apply"),
    //   None,
    //   "POSTGRES_URL" -> xaConfig.url
    // ).!
    // s"""POSTGRES_URL = ${url} ./sbt ";mg init ;mg update ;mg apply" """ !
  }

  override def afterAll {
    // drop the db for the test suite
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
