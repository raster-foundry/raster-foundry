package com.rasterfoundry.database

import doobie.implicits._
import org.scalatest._

class OrganizationFeatureDaoSpec
    extends FunSuite
    with Matchers
    with DBTestConfig {
  test("selection types") {
    OrganizationFeatureDao.query.list
      .transact(xa)
      .unsafeRunSync
      .length should be >= 0
  }
}
