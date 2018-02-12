package com.azavea.rf.database

import com.azavea.rf.datamodel.Band
import com.azavea.rf.database.Implicits._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import org.scalatest._

import java.util.UUID


class BandDaoSpec extends FunSuite with Matchers with IOChecker with DBTestConfig {

  test("insertion") {
    val testName = "Some Test Name"

    val transaction = for {
      bandIn <- BandDao.create(UUID.randomUUID, testName, 123, Array(1, 2, 3))
      bandOut <- BandDao.query.filter(fr"id = ${bandIn.id}").selectQ.unique
    } yield bandOut

    val result = transaction.transact(xa).unsafeRunSync
    result.name shouldBe testName
  }

  test("types") { check(BandDao.selectF.query[Band]) }
}

