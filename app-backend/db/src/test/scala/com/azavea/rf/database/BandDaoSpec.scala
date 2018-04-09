package com.azavea.rf.database

import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.Generators.Implicits._
import com.azavea.rf.database.Implicits._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import org.scalatest._

import java.util.UUID


/** We only need to test the list query, since insertion is checked when creating a
  * scene from a Scene.Create
  */
class BandDaoSpec extends FunSuite with Matchers with DBTestConfig {

  // list bands
  test("list bands") {
    BandDao.query.list.transact(xa).unsafeRunSync.length should be >= 0
  }
}

