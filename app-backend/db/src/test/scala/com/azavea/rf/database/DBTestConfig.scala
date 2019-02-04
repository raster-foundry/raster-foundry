package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._

import doobie._, doobie.implicits._
import doobie.hikari._
import doobie.postgres.implicits._
import cats.effect._
import cats.implicits._

import java.util.UUID
import java.util.concurrent._
import scala.concurrent.ExecutionContext

trait DBTestConfig {

  val connectExecutorService = Executors.newFixedThreadPool(10)
  val transactExecutorService = Executors.newFixedThreadPool(20)
  val connectEc = ExecutionContext.fromExecutorService(
    connectExecutorService,
    ExecutionContext.defaultReporter)
  val transactEc = ExecutionContext.fromExecutorService(
    transactExecutorService,
    ExecutionContext.defaultReporter
  )

  implicit val cs: ContextShift[IO] =
    IO.contextShift(transactEc)

  val xa =
    HikariTransactor.newHikariTransactor[IO](
      "org.postgresql.Driver",
      "jdbc:postgresql://database.service.rasterfoundry.internal/",
      "rasterfoundry",
      "rasterfoundry",
      connectEc,
      transactEc
    ) map { Transactor.after.set(_, HC.rollback) }

  implicit val transactor: Transactor[IO] =
    xa.use(trx => IO { trx }).unsafeRunSync

  val defaultPlatformId =
    UUID.fromString("31277626-968b-4e40-840b-559d9c67863c")

  val defaultUserQ = UserDao.unsafeGetUserById("default")
  val rootOrgQ = OrganizationDao.query
    .filter(UUID.fromString("9e2bef18-3f46-426b-a5bd-9913ee1ff840"))
    .selectQ
    .unique
  val defaultPlatformQ =
    PlatformDao.query.filter(defaultPlatformId).selectQ.unique
  val changeDetectionProjQ = ProjectDao.query
    .filter(fr"id = ${UUID.fromString("30fd336a-d360-4c9f-9f99-bb7ac4b372c4")}")
    .selectQ
    .unique
}
