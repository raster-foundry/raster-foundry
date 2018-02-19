package com.azavea.rf.database

import java.sql.Timestamp

import com.azavea.rf.datamodel._
import com.azavea.rf.database.Implicits._
import doobie._
import doobie.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.syntax.either._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.scalatest.imports._
import org.scalatest._
import io.circe._
import io.circe.syntax._
import java.util.UUID


class DatasourceDaoSpec extends FunSuite with Matchers with IOChecker with DBTestConfig {

  test("insertion") {
    val testName = "some test Name"
    val dummyJson = List(1, 2).asJson
    val now = new Timestamp((new java.util.Date()).getTime())

    val transaction = for {
      usr <- defaultUserQ
      org <- rootOrgQ
      dsIn <- {
        val ds = Datasource(
          UUID.randomUUID(), now, usr.id, now, usr.id, usr.id,
          org.id, testName, Visibility.Public, dummyJson, dummyJson, dummyJson)
        DatasourceDao.create(ds, usr)
      }
      dsOut <- DatasourceDao.query.filter(fr"id = ${dsIn.id}").selectQ.unique
    } yield dsOut

    val result = transaction.transact(xa).unsafeRunSync
    result.name shouldBe testName
  }

  test("types") { check(DatasourceDao.selectF.query[Datasource]) }
}

