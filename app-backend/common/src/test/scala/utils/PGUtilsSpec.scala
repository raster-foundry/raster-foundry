package com.azavea.rf.common.utils

import com.azavea.rf.common._

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Matchers, WordSpec}


/**
  * This set of tests ensures the basic functionality of the utilities
  * within PGUtils and which are depended upon for all database-reliant tests
  */
final class PGUtilsSpec extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with DBSpec {

  implicit val ec = system.dispatcher
  implicit val database = db

  val newdb = "test1"
  val copydb = "copyDB"

  "Database utilities" should {
    "Freely create and drop databases" in {
      // Drop newdb if it already exists, then create it
      PGUtils.dropDB(jdbcNoDBUrl, newdb, dbUser, dbPassword)
      PGUtils.createDB(jdbcNoDBUrl, newdb, dbUser, dbPassword)

      // Drop copyDB if it already exists, and copy test1 to it
      PGUtils.dropDB(jdbcNoDBUrl, copydb, dbUser, dbPassword)
      PGUtils.copyDB(jdbcNoDBUrl, newdb, copydb, dbUser, dbPassword)

      // Attempt to create newdb again (should fail)
      val caught = intercept[org.postgresql.util.PSQLException] {
        PGUtils.createDB(jdbcNoDBUrl, "copyDB", dbUser, dbPassword)
      }

      // Clean up databases
      PGUtils.dropDB(jdbcNoDBUrl, copydb, dbUser, dbPassword)
      PGUtils.dropDB(jdbcNoDBUrl, newdb, dbUser, dbPassword)

      // Check error message
      caught.getMessage shouldBe (s"""ERROR: database "copydb" already exists""")
    }
  }
}
