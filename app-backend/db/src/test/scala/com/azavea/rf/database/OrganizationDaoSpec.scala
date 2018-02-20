package com.azavea.rf.database

import com.azavea.rf.datamodel.Organization
import com.azavea.rf.database.Implicits._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import org.scalatest._


class OrganizationDaoSpec extends FunSuite with Matchers with IOChecker with DBTestConfig {

  test("insertion") {
    val testName = "test org"

    val transaction = for {
      orgIn <- OrganizationDao.create(testName)
      orgOut <- OrganizationDao.query.filter(fr"id = ${orgIn.id}").selectQ.unique
    } yield orgOut

    val result = transaction.transact(xa).unsafeRunSync
    result.name shouldBe testName
  }

  test("types") { check(OrganizationDao.selectF.query[Organization]) }
}

