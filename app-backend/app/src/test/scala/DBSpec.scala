package com.azavea.rf

import ammonite.ops._
import org.scalatest._

import com.azavea.rf.utils._


/**
  * Spec trait that uses BeforeAndAfterAll in order to instantiate a new,
  * freshly-migrated test database and destroy after the test has run.
  * Extend each Spec class that accesses the database with this trait in
  * order to make use of this behavior.
  */
trait DBSpec extends Suite with BeforeAndAfterAll with Config {
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
    PGUtils.copyDB(jdbcNoDBUrl, "testing_template", dbname, dbUser, dbPassword)
    db = new Database(jdbcNoDBUrl + dbname, dbUser, dbPassword)
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
object InitializeDB extends Config {
  // Working directory, needed for running %sbt commands
  implicit val wd = cwd

  // Check if the testing template DB exist. Create DB and initialize migrations if needed.
  PGUtils.runIfNoDB(jdbcNoDBUrl, dbUser, dbPassword) { () => {
    // Create the testing template DB
    PGUtils.createDB(jdbcNoDBUrl, "testing_template", dbUser, dbPassword)

    //Initialize migrations. POSTGRES_URL is passed as an env variable to target the correct DB.
    %sbt("mg init", POSTGRES_URL=s"${jdbcNoDBUrl}testing_template")
  }}

  // Run migrations -- scala-forklift requires that they be run twice
  0.to(1).foreach( _ =>
    %sbt("mg migrate", POSTGRES_URL=s"${jdbcNoDBUrl}testing_template")
  )
}
