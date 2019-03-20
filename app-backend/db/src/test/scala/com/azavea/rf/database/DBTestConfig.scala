package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.util.RFTransactor

import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import cats.implicits._

import java.util.UUID

trait DBTestConfig {

  val xa =
    RFTransactor.xaResource map { Transactor.after.set(_, HC.rollback) }

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
