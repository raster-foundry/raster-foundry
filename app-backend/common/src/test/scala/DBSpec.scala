package com.azavea.rf.common

import com.azavea.rf.database.{ Database, Config => DatabaseConfig }
import com.azavea.rf.common.utils._

import ammonite.ops._
import org.scalatest._


/**
  * Spec trait that uses BeforeAndAfterAll in order to instantiate a new,
  * freshly-migrated test database and destroy after the test has run.
  * Extend each Spec class that accesses the database with this trait in
  * order to make use of this behavior.
  */
trait DBSpec extends Suite with BeforeAndAfterAll with DatabaseConfig {
  // This triggers the one-time-only DB setup task for initializing the the test template DB
  InitializeDB
  private val driver = "org.postgresql.Driver"
  protected var db: Database = null

  // The test database that's created is named using the Spec class name
  private val dbname = getClass.getSimpleName.toLowerCase

  // Creates a fresh copy of the test DB for each test suite. Drops it first in case it exists.
  override def beforeAll() {
    super.beforeAll()
    PGUtils.dropDB(jdbcNoDBUrl, dbname, dbUser, dbPassword)
    PGUtils.copyDB(jdbcNoDBUrl, InitializeDB.testDB, dbname, dbUser, dbPassword)
    db = new Database(jdbcNoDBUrl + dbname, dbUser, dbPassword, 5, 5)
  }

  // Closes the connection pool and removes the test DB.
  override def afterAll() {
    super.afterAll()

    // Force close connections
    // If this is not performed, the connections stick around and can cause issues.
    db.closeConnectionPool()

    // Drop the test database
    // This must be performed after the connection pool is terminated,
    // otherwise the databases won't be dropped due to active users.
    PGUtils.dropDB(jdbcNoDBUrl, dbname, dbUser, dbPassword)
  }
}


/**
  * This object is referenced at the head of `DBSpec` - its purpose is to carry out
  *  any one-time-only setup required for DBSpec tests.
  */
object InitializeDB extends DatabaseConfig {
  // Working directory, needed for running %sbt commands
  implicit val wd = cwd

  import java.util.UUID
  val seed = UUID.randomUUID().toString.replace("-", "_")
  val testDB = s"testing_template${seed}"
  // Recreate the test database
  PGUtils.dropDB(jdbcNoDBUrl, testDB, dbUser, dbPassword)
  PGUtils.createDB(jdbcNoDBUrl, testDB, dbUser, dbPassword)

  // Run migrations
  %`./sbt`("mg init", POSTGRES_URL=s"${jdbcNoDBUrl}${testDB}")
  %`./sbt`(";mg update ;mg apply", POSTGRES_URL=s"${jdbcNoDBUrl}${testDB}")
}
