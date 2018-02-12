package com.azavea.rf.database

import com.azavea.rf.datamodel._
import com.azavea.rf.database.Implicits._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import org.scalatest._
import io.circe._
import io.circe.syntax._

import java.util.UUID


class DatasourceDaoSpec extends FunSuite with Matchers with IOChecker with DBTestConfig {

  test("insertion") {
    val testName = "some test Name"
    val dummyJson = List(1, 2).asJson

    val transaction = for {
      usr <- defaultUserQ
      org <- rootOrgQ
      dsIn <- DatasourceDao.create(usr, org.id, testName, Visibility.Public, Some(usr.id), dummyJson, dummyJson, dummyJson)
      dsOut <- DatasourceDao.query.filter(fr"id = ${dsIn.id}").selectQ.unique
    } yield dsOut

    val result = transaction.transact(xa).unsafeRunSync
    result.name shouldBe testName
  }

  test("types") { check(DatasourceDao.selectF.query[Datasource]) }
}

