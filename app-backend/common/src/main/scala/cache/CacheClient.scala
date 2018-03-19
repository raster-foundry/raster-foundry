package com.azavea.rf.common.cache

import java.util.concurrent.Executors

import net.spy.memcached._

import scala.concurrent._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.azavea.rf.common.{Config, RfStackTrace, RollbarNotifier}
import cats.data._
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.typesafe.scalalogging.LazyLogging

import scala.util.{Failure, Success}

object CacheClientThreadPool extends RollbarNotifier {
  implicit lazy val ec: ExecutionContext =
    ExecutionContext.fromExecutor(
      Executors.newFixedThreadPool(
        Config.memcached.threads,
        new ThreadFactoryBuilder().setNameFormat("cache-client-%d").build()
      )
    )

  implicit val system = ActorSystem("rollbar-notifier")
  implicit val materializer = ActorMaterializer()
}

class CacheClient(client: => MemcachedClient) extends LazyLogging with RollbarNotifier {

  import CacheClientThreadPool._
  implicit val system = ActorSystem("rollbar-notifier")
  implicit val materializer = ActorMaterializer()

  val cacheEnabled = Config.memcached.enabled

  def delete(key: String): Unit =
    if(cacheEnabled) {
      client.delete(key)
    }

  def setValue[T](key: String, value: T, ttlSeconds: Int = 0): Unit = {
    logger.debug(s"Setting Key: ${key} with TTL ${ttlSeconds}")
    val f = Future {
      client.set(key, ttlSeconds, value)

    }

    f.onFailure{
      case e => {
        logger.error(s"Error ${e.getMessage}")
        sendError(e)
      }
    }
  }

  // Suppress asInstanceOf warning because we can't pattern match on the returned type since it's
  // eliminated by type erasure
  @SuppressWarnings(Array("AsInstanceOf"))
  def getOrElseUpdate[CachedType](cacheKey: String, expensiveOperation: => Future[Option[CachedType]], doCache: Boolean = true): Future[Option[CachedType]] = {

    if (cacheEnabled && doCache) {

      // Note this blocks a thread in CacheClientThreadPool while waiting on the client's
      // own threadpool
      val futureCached = Future { client.asyncGet(cacheKey).get() }
      futureCached.flatMap(
        {
          case null => {
              // cache miss
              logger.debug(s"Cache Miss: ${cacheKey}")
              val futureCached: Future[Option[CachedType]] = expensiveOperation
              futureCached.onComplete {
                case Success(cachedValue) => {
                  cachedValue match {
                    case Some(v) => setValue(cacheKey, cachedValue)
                    case None => setValue(cacheKey, cachedValue, ttlSeconds = 300)
                  }
                }
                case Failure(e) => {
                  sendError(RfStackTrace(e))
                  logger.error(s"Cache Set Error: ${RfStackTrace(e)}")
                }
              }
              futureCached
            }
          case o => {
            logger.debug(s"Cache Hit: ${cacheKey}")
            Future.successful(o.asInstanceOf[Option[CachedType]])
          }
        }
      )
    } else {
      expensiveOperation
    }
  }

  def caching[T](cacheKey: String, doCache: Boolean = true)
             (mappingFunction: => Future[Option[T]]): Future[Option[T]] = {

    getOrElseUpdate[T](cacheKey, mappingFunction, doCache)
  }

  def cachingOptionT[T](cacheKey: String, doCache: Boolean = true)(mappingFunction: => OptionT[Future, T]): OptionT[Future, T] = {

    val futureOption = getOrElseUpdate[T](cacheKey, mappingFunction.value, doCache)
    OptionT(futureOption)
  }

}
