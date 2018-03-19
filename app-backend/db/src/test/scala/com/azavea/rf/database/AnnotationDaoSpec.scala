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

    val testLabels = ("Label 1", "label 2")

    val testAnnotation1 = Annotation.Create(
      None,
      testLabels._1,
      None,
      None,
      None,
      None,
      None
    )
    val testAnnotation2 = Annotation.Create(
      None,
      testLabels._2,
      None,
      None,
      None,
      None,
      None
    )

    val transaction = for {
      usr <- defaultUserQ
      org <- rootOrgQ
      proj <- changeDetectionProjQ
      annotationsIn <- AnnotationDao.insertAnnotations(List(testAnnotation1, testAnnotation2), proj.id, usr)
    } yield annotationsIn

    val result = transaction.transact(xa).unsafeRunSync

    result.length shouldBe 2
  }

  test("types") { check(AnnotationDao.selectF.query[Annotation]) }
}

