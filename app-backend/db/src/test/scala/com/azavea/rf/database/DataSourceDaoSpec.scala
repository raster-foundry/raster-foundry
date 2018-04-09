package com.azavea.rf.database

import java.sql.Timestamp

import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.Generators.Implicits._
import com.azavea.rf.database.Implicits._
import doobie._
import doobie.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.syntax.either._
import doobie.postgres._
import doobie.postgres.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers
import io.circe._
import io.circe.syntax._
import java.util.UUID


class DatasourceDaoSpec extends FunSuite with Matchers with Checkers with DBTestConfig {

  test("inserting a datasource") {
    check {
      forAll {
        (user: User, org: Organization, dsCreate: Datasource.Create) {
          // createDatasource should work
        }
      }
    }
  }

  test("getting a datasource by id") {
    check {
      forAll {
        (user: User, org: Organization, dsCreate: Datasource.Create) {
          // getting a datasource by id after inserting that datasource should work
        }
      }
    }
  }

  test("getting a datasource by id unsafely") {
    check {
      forAll {
        (user: User, org: Organization, dsCreate: Datasource.Create) {
          // getting a datasource by id with unsafeGetDatasourceById after inserting the datasource should work
        }
      }
    }
  }

  test("updating a datasource") {
    check {
      forAll {
        (user: User, org: Organization, dsCreate: Datasource.Create, dsUpdate: Datasource.Create) {
          // creating a datasource, then updating it from dsUpdate, should work
        }
      }
    }
  }

  test("deleting a datasource") {
    check {
      forAll {
        (user: User, org: Organization, dsCreate: Datasource.Create) {
          // inserting a datasource then deleting it should return a 1
        }
      }
    }
  }

  test("listing datasources") {
    DatasourceDao.query.list.tranact(xa).unsafeRunSync.length should be >= 0
  }

}

