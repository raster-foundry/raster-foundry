package com.rasterfoundry.database

import doobie.implicits._

import org.scalatest._

class OrganizationFeatureDaoSpec
    extends FunSuite
    with Matchers
    with DBTestConfig {
  test("selection types") {
    xa.use(t => OrganizationFeatureDao.query.list.transact(t))
      .unsafeRunSync
      .length should be >= 0
  }
}
