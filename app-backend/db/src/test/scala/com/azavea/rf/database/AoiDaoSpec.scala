package com.azavea.rf.database

import com.azavea.rf.datamodel.AOI
import com.azavea.rf.database.meta.RFMeta._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import org.scalatest._


class AoiDaoSpec extends FunSuite with Matchers with IOChecker {

  val transactor = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver", "jdbc:postgresql://database.service.rasterfoundry.internal/", "rasterfoundry", "rasterfoundry"
  )

  test("select") { check(AoiDao.Statements.select.query[AOI]) }
}

