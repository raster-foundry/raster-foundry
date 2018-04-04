package com.azavea.rf.database

import com.azavea.rf.database.Implicits._

import doobie._, doobie.implicits._
import doobie.hikari._, doobie.hikari.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._

import java.util.UUID


trait DBTestConfig {

  val xa: Transactor[IO] =
    Transactor.after.set(
      Transactor.fromDriverManager[IO](
        "org.postgresql.Driver",
        "jdbc:postgresql://database.service.rasterfoundry.internal/",
        "rasterfoundry",
        "rasterfoundry"
      ),
      HC.rollback
    )

  implicit val transactor = xa

  val defaultPlatformId = UUID.fromString("31277626-968b-4e40-840b-559d9c67863c")

  val defaultUserQ = UserDao.unsafeGetUserById("default")
  val rootOrgQ = OrganizationDao.query.filter(UUID.fromString("9e2bef18-3f46-426b-a5bd-9913ee1ff840")).selectQ.unique
  val defaultPlatformQ = PlatformDao.query.filter(defaultPlatformId).selectQ.unique
  val changeDetectionProjQ = ProjectDao.query.filter(fr"id = ${UUID.fromString("30fd336a-d360-4c9f-9f99-bb7ac4b372c4")}").selectQ.unique
}
