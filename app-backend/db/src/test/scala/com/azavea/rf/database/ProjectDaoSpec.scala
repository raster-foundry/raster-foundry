package com.azavea.rf.database

import com.azavea.rf.datamodel._
import com.azavea.rf.database.Implicits._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import org.scalatest._


class ProjectDaoSpec extends FunSuite with Matchers with IOChecker with DBTestConfig {

  test("insertion") {
    val testName = "test project name"

    val transaction = for {
      usr <- defaultUserQ
      org <- rootOrgQ
      projectIn <- ProjectDao.create(
        usr, Some(usr.id), org.id, testName, "description", Visibility.Public,
        Visibility.Public, true, 123L, List("tags"), false, None
      )
      projectOut <- ProjectDao.query.filter(fr"id = ${projectIn.id}").selectQ.unique
    } yield projectOut

    val result = transaction.transact(xa).unsafeRunSync
    result.name shouldBe testName
  }

  test("types") { check(ProjectDao.selectF.query[Project]) }
}

