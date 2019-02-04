package com.rasterfoundry.database

import doobie.implicits._
import org.scalatest._

class FeatureFlagDaoSpec extends FunSuite with Matchers with DBTestConfig {
  test("types") {
    xa.use(t => FeatureFlagDao.query.list.transact(t))
      .unsafeRunSync
      .length should be >= 0
  }
}
