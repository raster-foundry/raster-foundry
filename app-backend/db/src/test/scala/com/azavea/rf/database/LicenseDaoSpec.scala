package com.rasterfoundry.database

import doobie.implicits._
import org.scalatest._

class LicenseDaoSpec extends FunSuite with Matchers with DBTestConfig {
  test("selection types") {
    LicenseDao.query.list.transact(xa).unsafeRunSync.length should be >= 0
  }
}
