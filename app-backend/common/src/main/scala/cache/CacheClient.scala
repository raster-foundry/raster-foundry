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

class CacheClient(client: => MemcachedClient) extends LazyLogging {

  import CacheClientThreadPool._

  val cacheEnabled = Config.memcached.enabled

  def delete(key: String) =
    if(cacheEnabled) {
      client.delete(key)
    }

  def setValue[T](key: String, value: T, ttlSeconds: Int = 0): Unit = {
    Future {
      client.set(key, ttlSeconds, value)
    }
  }

  def getOrElseUpdate[CachedType](cacheKey: String, expensiveOperation: => Future[Option[CachedType]], ttlSeconds: Int = 0, doCache: Boolean = true): Future[Option[CachedType]] = {

    if (cacheEnabled && doCache) {

      // Note this blocks a thread in CacheClientThreadPool while waiting on the client's
      // own threadpool
      val futureCached = Future { client.asyncGet(cacheKey).get() }
      futureCached.flatMap({ value =>
        if (value != null) {
          // cache hit
          Future.successful(Some(value.asInstanceOf[CachedType]))
        } else {
          // cache miss
          val futureCached: Future[Option[CachedType]] = expensiveOperation
          futureCached.onComplete {
            case Success(cachedValue) => {
              cachedValue match {
                case Some(v) => setValue(cacheKey, v)
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
      })
    } else {
      expensiveOperation
    }
  }

  def caching[T](cacheKey: String, ttlSeconds: Int = 0,
    doCache: Boolean = true)(mappingFunction: => Future[Option[T]]): Future[Option[T]] = {

    getOrElseUpdate[T](cacheKey, mappingFunction, ttlSeconds, doCache)
  }

  def cachingOptionT[T](cacheKey: String, ttlSeconds: Int = 0, doCache: Boolean = true)(mappingFunction: => OptionT[Future, T]): OptionT[Future, T] = {

    val futureOption = getOrElseUpdate[T](cacheKey, mappingFunction.value, ttlSeconds, doCache)
    OptionT(futureOption)
  }

}
