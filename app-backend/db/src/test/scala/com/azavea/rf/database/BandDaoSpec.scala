package com.rasterfoundry.database

import doobie.implicits._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/** We only need to test the list query, since insertion is checked when creating a
  * scene from a Scene.Create
  */
class BandDaoSpec extends AnyFunSuite with Matchers with DBTestConfig {

  // list bands
  test("list bands") {
    BandDao.query.list.transact(xa).unsafeRunSync.length should be >= 0
  }
}
