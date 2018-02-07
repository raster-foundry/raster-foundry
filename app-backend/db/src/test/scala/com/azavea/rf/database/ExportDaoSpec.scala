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


class ExportDaoSpec extends FunSuite with Matchers with IOChecker with DBTestConfig {

  test("insertion") {
    val testStatus = ExportStatus.NotExported

    val transaction = for {
      usr <- defaultUserQ
      org <- rootOrgQ
      proj <- changeDetectionProjQ
      exportIn <- ExportDao.create(usr, org.id, Some(proj.id), testStatus, ExportType.Dropbox, Visibility.Public, Some(usr.id), None, JsonObject.empty.asJson)
      exportOut <- ExportDao.query.filter(fr"id = ${exportIn.id}").selectQ.unique
    } yield exportOut

    val result = transaction.transact(xa).unsafeRunSync
    result.exportStatus shouldBe testStatus
  }

  test("select") { check(ExportDao.selectF.query[Export]) }
}

