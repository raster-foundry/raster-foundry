package com.azavea.rf.database

import com.azavea.rf.datamodel._
import com.azavea.rf.database.Implicits._

import doobie._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.scalatest.imports._

import org.scalatest._

class OrganizationFeatureDaoSpec extends FunSuite with Matchers with IOChecker with DBTestConfig {
  test("selection types") {
    check(OrganizationFeatureDao.selectF.query[OrgFeatures])
  }
}
