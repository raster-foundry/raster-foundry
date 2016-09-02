package com.azavea.rf.utils

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import scala.concurrent.Await
import scala.concurrent.duration._
import org.postgresql.util.PSQLException
import slick.driver.PostgresDriver
import slick.driver.PostgresDriver.api._
import scala.util.Try


object PGUtils {

  private val actionTimeout = 10 second
  private val driver = "org.postgresql.Driver"

  def createDB(jdbcNoDBUrl: String, dbName: String, user: String, pwd: String): Unit = {
    println(s"creating db $dbName")
    //val onlyHostNoDbUrl = s"jdbc:postgresql://$host/"
    using(Database.forURL(jdbcNoDBUrl, user = user, password = pwd, driver = driver)) { conn =>
      Await.result(conn.run(sqlu"CREATE DATABASE #$dbName"), actionTimeout)
    }
  }

  def copyDB(jdbcNoDBUrl: String, sourceDB: String, targetDB: String, user: String, pwd: String): Unit = {
    println(s"copying $sourceDB to $targetDB")
    using(Database.forURL(jdbcNoDBUrl, user = user, password = pwd, driver = driver)) { conn =>
      // Terminate connections to source db to avoid errors
      //Await.result(conn.run(sqlu"SELECT pg_terminate_backend(pg_stat_activity.pid) FROM pg_stat_activity WHERE pg_stat_activity.datname = '#$sourceDB' AND pid <> pg_backend_pid()"), actionTimeout)
      Await.result(conn.run(sqlu"CREATE DATABASE #$targetDB WITH TEMPLATE #$sourceDB"), actionTimeout)
    }
  }

  def dropDB(jdbcNoDBUrl: String, dbName: String, user: String, pwd: String): Unit = {
    println(s"dropping db $dbName")
    //val onlyHostNoDbUrl = s"jdbc:postgresql://$host/"
    try {
      using(Database.forURL(jdbcNoDBUrl, user = user, password = pwd, driver = driver)) { conn =>
        Await.result(conn.run(sqlu"DROP DATABASE #$dbName"), actionTimeout)
      }
    } catch {
      // ignore failure due to db not exist
      case e:PSQLException => if (e.getMessage.equals(s""""database "$dbName" does not exist""")) {/* do nothing */}
      case e:Throwable => throw e // escalate other exceptions
    }
  }

  // This function needs to be fleshed out slightly - tests currently rely on catching exceptions
  //  to verify that a db already exists
  //def dbExists(jdbcNoDBUrl: String, dbName: String, user: String, pwd: String): Boolean = {
  //  println(s"testing db $dbName")
  //  val dbio: DBIO[Int] = sqlu"SELECT count(*) FROM pg_catalog.pg_database WHERE datname = '#$dbName'"
  //  try {
  //    using(Database.forURL(jdbcNoDBUrl, user = user, password = pwd, driver = driver)) { conn =>
  //      if (Await.result(conn.run(dbio), actionTimeout) < 1) false else true
  //    }
  //  } catch {
  //    case e:PSQLException =>
  //      if (e.getMessage.equals(s""""database "$dbName" does not exist""")) false else throw e
  //    case e:Throwable => throw e
  //  }
  //}

  /**
   * Automatically close a resource with method 'close'
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
