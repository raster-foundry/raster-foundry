package com.azavea.rf.database

import com.azavea.rf.datamodel._
import com.azavea.rf.database.Implicits._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import org.scalatest._

import java.util.UUID

class AnnotationDaoSpec extends FunSuite with Matchers with IOChecker with DBTestConfig {

  test("insertion") {
    val testLabel: String = "this is a test!"

    val transaction = for {
      usr <- defaultUserQ
      org <- rootOrgQ
      proj <- changeDetectionProjQ
      annotationIn <- AnnotationDao.create(proj.id, usr, None, org.id, testLabel, None, None, None, None, None)
      annotationOut <- AnnotationDao.query.filter(fr"id = ${annotationIn.id}").selectQ.unique
    } yield annotationOut

    val result = transaction.transact(xa).unsafeRunSync
    result.label shouldBe testLabel
  }

  test("types") { check(AnnotationDao.selectF.query[Annotation]) }
}

