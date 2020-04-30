package com.rasterfoundry.database

import doobie.implicits._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class LicenseDaoSpec extends AnyFunSuite with Matchers with DBTestConfig {
  test("selection types") {
    LicenseDao.query.list.transact(xa).unsafeRunSync.length should be >= 0
  }
}
