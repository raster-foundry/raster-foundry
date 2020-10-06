package com.rasterfoundry.database

import doobie.implicits._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class OrganizationFeatureDaoSpec
    extends AnyFunSuite
    with Matchers
    with DBTestConfig {
  test("selection types") {
    OrganizationFeatureDao.query.list
      .transact(xa)
      .unsafeRunSync
      .length should be >= 0
  }
}
