package com.azavea.rf.utils

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

import slick.driver.PostgresDriver.api._


/**
  * Utilities for running low-level PostgreSQL commands
  */
object PGUtils {

  private val actionTimeout = 10 second
  private val driver = "org.postgresql.Driver"


  /**
    * Creates a database
    *
    * @param jdbcNoDBurl url of the database server (with no db component)
    * @param dbName name of the database
    * @param user user name for the database
    * @param pwd password for the database
    */
  def createDB(jdbcNoDBUrl: String, dbName: String, user: String, pwd: String): Unit = {
    using(Database.forURL(jdbcNoDBUrl, user = user, password = pwd, driver = driver)) { conn =>
      Await.result(conn.run(sqlu"CREATE DATABASE #$dbName"), actionTimeout)
    }
  }

  /**
    * Creates a new database as a copy of another
    *
    * @param jdbcNoDBurl url of the database server (with no db component)
    * @param sourceDB name of the source database that will be copied from
    * @param targetDB name of the target database that will be created
    * @param user user name for the database
    * @param pwd password for the database
    */
  def copyDB(jdbcNoDBUrl: String, sourceDB: String, targetDB: String,
    user: String, pwd: String): Unit = {
    using(Database.forURL(jdbcNoDBUrl, user = user, password = pwd, driver = driver)) { conn =>
      Await.result(
        conn.run(sqlu"CREATE DATABASE #$targetDB WITH TEMPLATE #$sourceDB"),
        actionTimeout
      )
    }
  }

  /**
    * Drops a database
    *
    * @param jdbcNoDBurl url of the database server (with no db component)
    * @param dbName name of the database
    * @param user user name for the database
    * @param pwd password for the database
    */
  def dropDB(jdbcNoDBUrl: String, dbName: String, user: String, pwd: String): Unit = {
    using(Database.forURL(jdbcNoDBUrl, user = user, password = pwd, driver = driver)) { conn =>
      Await.result(conn.run(sqlu"DROP DATABASE IF EXISTS #$dbName"), actionTimeout)
    }
  }

  /**
    * Runs a function only if the database does not exist
    *
    * @param jdbcNoDBurl url of the database server (with no db component)
    * @param user user name for the database
    * @param pwd password for the database
    * @param fnIfNoDB function to run if the database does not exist
    */
  def runIfNoDB(jdbcNoDBUrl: String, user: String, pwd: String)
    (fnIfNoDB: () => Unit): Unit = {
    using(Database.forURL(jdbcNoDBUrl, user = user, password = pwd, driver = driver)) { conn =>
      Await.result(
        conn.run(sql"SELECT 1 FROM pg_database WHERE datname='testing_template'".as[Int].headOption),
        actionTimeout
      ) match {
        case None => fnIfNoDB()
        case _ => // Do nothing. Only run the function when the DB doesn't exist (result is None)
      }
    }
  }

  /**
    * Automatically closes a resource with method 'close'
    */
  private def using[A <: {def close() : Unit}, B](resource: A)(f: A => B): B =
    try {
      f(resource)
    } finally {
      Try {
        resource.close()
      }.failed.foreach(err => throw new Exception(s"failed to close $resource", err))
    }
}
