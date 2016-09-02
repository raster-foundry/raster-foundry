package com.azavea.rf

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import scala.concurrent.Await
import scala.concurrent.duration._
import org.postgresql.util.PSQLException
import scala.util.Try

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Matchers, WordSpec}

import com.azavea.rf.utils._

/**
 * This set of tests ensures the basic functionality of the utilities
 *  within PGUtils and which are depended upon for all database-reliant tests
 */
class PGUtilsSpec extends WordSpec
                     with Matchers
                     with ScalatestRouteTest
                     with Config
                     with Router {

  implicit val ec = system.dispatcher
  implicit val database = new Database(s"jdbc:postgresql://0.0.0.0/slick_tests", "postgres", "secret")
  val newdb = "test1"

  "Database utilities" should {
    "Freely create and drop databases" in {
      // create
      PGUtils.createDB("jdbc:postgresql://0.0.0.0/", newdb, "postgres", "secret")
      // try to create again
      val caught = intercept[org.postgresql.util.PSQLException] {
        PGUtils.createDB("jdbc:postgresql://0.0.0.0/", newdb, "postgres", "secret")
      }
      // drop that db
      PGUtils.dropDB("jdbc:postgresql://0.0.0.0/", newdb, "postgres", "secret")

      // create again to show that drop worked
      PGUtils.createDB("jdbc:postgresql://0.0.0.0/", newdb, "postgres", "secret")

      // drop to clean house
      PGUtils.dropDB("jdbc:postgresql://0.0.0.0/", newdb, "postgres", "secret")

      // Check error message
      caught.getMessage shouldBe (s"""ERROR: database "${newdb}" already exists""")
    }

    "Copy databases" in {
      // create
      PGUtils.createDB("jdbc:postgresql://0.0.0.0/", newdb, "postgres", "secret")
      // copy
      PGUtils.copyDB("jdbc:postgresql://0.0.0.0/", newdb, "copyDB", "postgres", "secret")

      // attempt to create in the copied namespace
      val caught = intercept[org.postgresql.util.PSQLException] {
        PGUtils.createDB("jdbc:postgresql://0.0.0.0/", "copyDB", "postgres", "secret")
      }

      // clean house
      PGUtils.dropDB("jdbc:postgresql://0.0.0.0/", "copyDB", "postgres", "secret")
      PGUtils.dropDB("jdbc:postgresql://0.0.0.0/", newdb, "postgres", "secret")

      // Check error message
      caught.getMessage shouldBe (s"""ERROR: database "copydb" already exists""")
    }
  }
}

