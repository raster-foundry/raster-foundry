package com.azavea.rf.database

import com.azavea.rf.datamodel.AOI
import com.azavea.rf.database.Implicits._

import io.circe._
import io.circe.syntax._
import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import geotrellis.slick._
import geotrellis.vector._
import org.scalatest._


class AoiDaoSpec extends FunSuite with Matchers with IOChecker with DBTestConfig {

  test("insertion") {
    val testPoly = Projected(MultiPolygon(Polygon(Point(1, 0), Point(1, 1), Point(0, 1), Point(1, 0))), 3857)

    val transaction = for {
      usr <- defaultUserQ
      org <- rootOrgQ
      aoiIn <- AoiDao.create(usr, Some(usr.id), org.id, testPoly, List(1,2).asJson)
      aoiOut <- AoiDao.query.filter(fr"id = ${aoiIn.id}").selectQ.unique
    } yield aoiOut

    val result = transaction.transact(xa).unsafeRunSync
    result.area shouldBe testPoly
  }

  test("types") { check(AoiDao.selectF.query[AOI]) }
}

