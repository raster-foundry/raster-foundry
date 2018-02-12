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


class ImageDaoSpec extends FunSuite with Matchers with IOChecker with DBTestConfig {

  test("insertion") {
    val testFileList = List("file1", "typo")

    val transaction = for {
      usr <- defaultUserQ
      org <- rootOrgQ
      proj <- changeDetectionProjQ
      scene <- SceneDao.query.filter(fr"id = ${UUID.fromString("a9ce69c2-f87e-4119-863d-d570afb53983")}").selectQ.unique
      imageIn <- ImageDao.create(
        usr, org.id, 123456L, Visibility.Public, "filename", "http://sourceUri", scene.id,
        List(1, 2).asJson, Some(usr.id), 15.0.toFloat, testFileList
      )
      imageOut <- ImageDao.query.filter(fr"id = ${imageIn.id}").selectQ.unique
    } yield imageOut

    val result = transaction.transact(xa).unsafeRunSync
    result.metadataFiles shouldBe testFileList
  }

  test("types") { check(ImageDao.selectF.query[Image]) }
}

