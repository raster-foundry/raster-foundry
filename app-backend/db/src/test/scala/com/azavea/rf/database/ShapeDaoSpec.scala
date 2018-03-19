package com.azavea.rf.database

import com.azavea.rf.datamodel.{User, Organization, Shape}
import com.azavea.rf.database.Implicits._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import cats.syntax.option._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import org.scalatest._

import geotrellis.slick.Projected
import geotrellis.vector.{MultiPolygon, Polygon, Point}


class ShapeDaoSpec extends FunSuite with Matchers with IOChecker with DBTestConfig {

  test("types") { check(ShapeDao.selectF.query[Shape]) }
  test("insertion") {
    val testPoly = Projected(MultiPolygon(Polygon(Point(1, 0), Point(1, 1), Point(0, 1), Point(1, 0))), 3857)
    val someShapes = (user : User, org : Organization) => Seq(
      Shape.Create(None, org.id, "Good shape", None, None),
      Shape.Create(None, org.id, "Great shape", "A great shape".some, testPoly.some),
      Shape.Create(user.id.some, org.id, "Best shape", "The best shape".some, testPoly.some)
    )
    val transaction = for {
      user <- defaultUserQ
      org <- rootOrgQ
      shapes = someShapes(user, org)
      shapesIn <- ShapeDao.insertShapes(shapes, user)
    } yield (shapesIn)

    val result = transaction.transact(xa).unsafeRunSync

    result.length shouldBe 3
  }
}

