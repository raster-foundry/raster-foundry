package com.azavea.rf.common.cache

import java.util.concurrent.Executors

import net.spy.memcached._

import scala.concurrent._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.azavea.rf.common.Config
import cats.data._
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.typesafe.scalalogging.LazyLogging

object CacheClientThreadPool {
  implicit lazy val ec: ExecutionContext =
    ExecutionContext.fromExecutor(
      Executors.newFixedThreadPool(
        Config.memcached.threads,
        new ThreadFactoryBuilder().setNameFormat("cache-client-%d").build()
      )
    )
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

  def getOrElseUpdate[CachedType](cacheKey: String, expensiveOperation: => Future[CachedType],
    ttlSeconds: Int = 0, doCache: Boolean = true): Future[CachedType] = {

    if (cacheEnabled && doCache) {

      // Note this blocks a thread in CacheClientThreadPool while waiting on the client's
      // own threadpool
      val futureCached = Future { client.asyncGet(cacheKey).get() }
      futureCached.flatMap({ value =>
        if (value != null) {
          // cache hit
          Future.successful(value.asInstanceOf[CachedType])
        } else {
          // cache miss
          val futureCached: Future[CachedType] = expensiveOperation
          futureCached.foreach { cachedValue =>
            try {
              setValue(cacheKey, cachedValue)
            } catch {
              case e: Exception => logger.info(s"Cache Set Error: ${e.getMessage}")
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
    doCache: Boolean = true)(mappingFunction: => Future[T]): Future[T] = {

    getOrElseUpdate[T](cacheKey, mappingFunction, ttlSeconds, doCache)
  }

  def cachingOptionT[T](cacheKey: String, ttlSeconds: Int = 0,
    doCache: Boolean = true)(mappingFunction: => OptionT[Future, T]): OptionT[Future, T] = {

    val futureOption = getOrElseUpdate[Option[T]](cacheKey, mappingFunction.value, ttlSeconds, doCache)
    OptionT(futureOption)
  }

}
