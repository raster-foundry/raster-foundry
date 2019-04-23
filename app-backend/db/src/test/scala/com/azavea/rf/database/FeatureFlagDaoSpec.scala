package com.rasterfoundry.database

import doobie.implicits._
import org.scalatest._

class FeatureFlagDaoSpec extends FunSuite with Matchers with DBTestConfig {
  test("types") {
    FeatureFlagDao.query.list.transact(xa).unsafeRunSync.length should be >= 0
  }
}
