package com.rasterfoundry.database

import com.rasterfoundry.datamodel._
import com.rasterfoundry.database.Implicits._

import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.scalatest.imports._

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
