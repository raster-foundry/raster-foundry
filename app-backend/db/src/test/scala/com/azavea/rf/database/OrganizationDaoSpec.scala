package com.azavea.rf.database

import com.azavea.rf.datamodel.{Organization, Platform}
import com.azavea.rf.database.Implicits._

import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import org.scalatest._


class OrganizationDaoSpec extends FunSuite with Matchers with IOChecker with DBTestConfig {

  test("insertion") {
    val testPlatformName = "test platform"
    val testOrgName = "test org"

    val transaction = for {
      userIn <- defaultUserQ
      platformIn <- PlatformDao.create(Platform.Create(testPlatformName, userIn).toPlatform)
      orgIn <- OrganizationDao.create(Organization.Create(testOrgName, platformIn.id).toOrganization)
      orgOut <- OrganizationDao.query.filter(fr"id = ${orgIn.id}").selectQ.unique
    } yield orgOut

    val result = transaction.transact(xa).unsafeRunSync
    result.name shouldBe testOrgName
  }

  test("types") { check(OrganizationDao.selectF.query[Organization]) }
}

