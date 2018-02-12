package com.azavea.rf.database

import com.azavea.rf.datamodel.Shape
import com.azavea.rf.database.Implicits._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import org.scalatest._


class ShapeDaoSpec extends FunSuite with Matchers with IOChecker with DBTestConfig {

  test("types") { check(ShapeDao.selectF.query[Shape]) }
}

