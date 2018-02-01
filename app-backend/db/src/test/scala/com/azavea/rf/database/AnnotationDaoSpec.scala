package com.azavea.rf.database

import com.azavea.rf.datamodel._

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
      usr <- UserDao.select("default")
      org <- OrganizationDao.select(UUID.fromString("9e2bef18-3f46-426b-a5bd-9913ee1ff840"))
      proj <- ProjectDao.select(UUID.fromString("30fd336a-d360-4c9f-9f99-bb7ac4b372c4"))
      annotationIn <- AnnotationDao.create(proj.id, usr, None, org.id, testLabel, None, None, None, None, None)
      annotationOut <- AnnotationDao.select(annotation1.id)
    } yield annotation2

    val result = transaction.transact(xa).unsafeRunSync
    result.label shouldBe testLabel
  }

  test("select typecheck") { check(AnnotationDao.Statements.select.query[Annotation]) }
}

