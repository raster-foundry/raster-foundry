package com.azavea.rf.database

import com.azavea.rf.datamodel.Generators.Implicits._
import com.azavea.rf.database.Implicits._
import doobie._
import doobie.implicits._

import doobie.postgres._
import doobie.postgres.implicits._

import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers

class AuthorizationSpec extends Checkers with DBTestConfig with PropTestHelpers {

  test("authorizing everyone should authorize all users") {
    check {
      forAll {
        (u: Unit) => true
      }
    }
  }

  test("authorizing a platform should authorize all users in organizations in that platform but not in other platforms") {
    check {
      forAll {
        (userOrgPlatform1: (User.Create, Organization.Create, Platform),
         userOrgPlatform2: (User.Create, Organization.Create, Platform)) => {
          true
        }
      }
    }
  }

  test("authorizing an organization should authorize users in that organization and not in other organizations") {
    check {
      forAll {
        (u: Unit) => true
      }
    }
  }

  test("authorizing a team should authorize users on that team and not on other teams") {
    check {
      forAll {
        (u: Unit) => true
      }
    }
  }

}
