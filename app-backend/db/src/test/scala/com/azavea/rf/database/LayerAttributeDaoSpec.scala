package com.azavea.rf.database

import com.azavea.rf.datamodel.LayerAttribute
import com.azavea.rf.database.Implicits._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import io.circe.syntax._
import org.scalatest._


class LayerAttributeDaoSpec extends FunSuite with Matchers with IOChecker with DBTestConfig {

  test("types") { check(LayerAttributeDao.selectF.query[LayerAttribute]) }

  test("insertion") {
    val testLayerAttribute = LayerAttribute("testLayerAttr", 13, "testLayerName", ().asJson)
    val transaction = LayerAttributeDao.insertLayerAttribute(testLayerAttribute)

    val result = transaction.transact(xa).unsafeRunSync

    result shouldBe 1
  }
}

