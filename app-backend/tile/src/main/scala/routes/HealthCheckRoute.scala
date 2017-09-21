package com.azavea.rf.tile.routes

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.syntax._

import com.azavea.rf.common.cache.kryo.KryoMemcachedClient
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.Users

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Try, Random, Success, Failure}

object HealthCheckRoute extends LazyLogging {
  lazy val memcachedClient = KryoMemcachedClient.DEFAULT
  lazy val database = Database.DEFAULT
  def root: Route =
    complete {
      (checkCacheHealth, checkDatabaseConn) match {
        case (true, true) =>
          HttpResponse(
            200,
            entity= Map(
              "service" -> "tile",
              "status" -> "OK",
              "active threads" -> Thread.activeCount.toString,
              "cacheHealth" -> "OK",
              "databaseHealth" -> "OK"
            ).asJson.noSpaces
          )
        case (false, true) =>
          HttpResponse(503, entity=Map("failing" -> Seq("cache")).asJson.noSpaces)
        case (true, false) =>
          HttpResponse(503, entity=Map("failing" -> Seq("database")).asJson.noSpaces)
        case (false, false) =>
          HttpResponse(503, entity=Map("failing" -> Seq("cache", "database")).asJson.noSpaces)
      }
    }

  /** Attempt to read a random key from the cache
    *
    * While the key won't actually be present in the cache, if the cache is reachable,
    * the return from the try will be a Success(null), while if the cache is unreachable,
    * the Try will return a Failure
    *
    * memcachedClient.get doesn't return a native Future, but some fancy memcached
    * Future type that doesn't play well with native futures and makes me sad.
    */
  def checkCacheHealth: Boolean = {
    val cacheKey = Random.nextString(8)
    Try { memcachedClient.get(cacheKey) } match {
      case Success(_) => true
      case Failure(_) =>
        logger.error(
          s"Failed reading from memcached client for key $cacheKey"
        )
        false
    }
  }

  /** Attempt to read a record from the users table
    *
    * If we find at least one user, we were successful.
    *
    * Await the result of the database execution to get something that will
    * play more nicely with memcached's Future type in checkCacheHealth
    */
  def checkDatabaseConn: Boolean = {
    import database.driver.api._
    val countAction = Users.take(1).length.result
    Try {
      Await.result(
        database.db.run { countAction }, 3 seconds
      )
    } match {
      case Success(x) if x > 0 => true
      case Success(_) =>
        logger.error(
          "No records found for users for some reason"
        )
        false
      case Failure(_) =>
        logger.error(
          "Failed reading from the database"
        )
        false
    }
  }
}
