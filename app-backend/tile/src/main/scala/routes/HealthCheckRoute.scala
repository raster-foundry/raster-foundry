package com.azavea.rf.tile.routes

import com.azavea.rf.common.cache.kryo.KryoMemcachedClient
import com.azavea.rf.database.{UserDao}
import com.azavea.rf.database.util.RFTransactor

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import io.circe.syntax._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Try, Random, Success, Failure}

object HealthCheckRoute extends LazyLogging {
  lazy val memcachedClient = KryoMemcachedClient.DEFAULT
  implicit val xa = RFTransactor.xa

  def root: Route =
    complete {
      Seq(checkCacheReadHealth, checkCacheWriteHealth, checkDatabaseConn).flatten match {
        case noMessage if noMessage.length == 0 =>
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
        case messages =>
          HttpResponse(503, entity=Map("failing" -> messages).asJson.noSpaces)
      }
    }

  /** Attempt to read a random key from the cache
    *
    * While the key won't actually be present in the cache, if the cache is reachable,
    * the return from the try will be a Success(null), while if the cache is unreachable,
    * the Try will return a Failure
    */
  def checkCacheReadHealth: Option[String] = {
    val cacheKey = Random.nextString(8)
    Try { memcachedClient.get(cacheKey) } match {
      case Success(_) => None
      case Failure(_) =>
        logger.error(
          s"Failed reading from memcached client for key $cacheKey"
        )
        Some("Failed reading from memcached client")
    }
  }

  /** Attempt to write a random key to the cache
    *
    * If the write fails, report and return the message that
    * memcached sent back about the failure
    */
  def checkCacheWriteHealth: Option[String] = {
    val cacheKey = Random.nextString(8)
    val cacheValue = Random.nextInt(1000)
    Try { memcachedClient.set(cacheKey, cacheValue, 5).getStatus } match {
      case Success(x) if x.isSuccess => None
      case Success(x) =>
        val message = s"Failed writing to memcached client. Message: ${x.getMessage}"
        logger.error(message)
        Some(message)
      case Failure(x) =>
        val message = s"Failed writing to memcached client. Mesage: ${x.getMessage}"
        logger.error(message)
        Some(message)
    }
  }

  /** Attempt to read a record from the users table
    *
    * If we find at least one user, we were successful.
    *
    * Await the result of the database execution to get something that will
    * play more nicely with memcached's Future type in checkCacheHealth
    */
  def checkDatabaseConn: Option[String] = {
    Try {
      Await.result(
        UserDao.query.list(1).transact(xa).unsafeToFuture, 3 seconds
      )
    } match {
      case Success(List(_)) => None
      case Success(_) =>
        val message = "No records found for users for some reason"
        logger.error(message)
        Some(message)
      case Failure(_) =>
        val message = "Database unreachable"
        logger.error(message)
        Some(message)
    }
  }
}
