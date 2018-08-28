package com.azavea.rf.common.cache

import java.util.concurrent.Executors

import cats.data._
import com.azavea.rf.common.{Config, RfStackTrace, RollbarNotifier}
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.typesafe.scalalogging.LazyLogging
import net.spy.memcached._

import scala.annotation.tailrec
import scala.concurrent._
import scala.util.{Failure, Success}

object CacheClientThreadPool extends RollbarNotifier {
  implicit lazy val ec: ExecutionContext =
    ExecutionContext.fromExecutor(
      Executors.newFixedThreadPool(
        Config.memcached.threads,
        new ThreadFactoryBuilder().setNameFormat("cache-client-%d").build()
      )
    )
}

class CacheClient(client: => MemcachedClient)
    extends LazyLogging
    with RollbarNotifier {

  import CacheClientThreadPool._

  val cacheEnabled: Boolean = Config.memcached.enabled
  val localCacheEnabled: Boolean = Config.memcached.localCacheEnabled
  val localCacheSize: Int = Config.memcached.localCacheSize
  val keySize: Int = Config.memcached.keySize

  val retryMaxMillis = 30000
  val retrySleepMillis = 25
  val maxRetryDepth: Int = retryMaxMillis / retrySleepMillis

  val localCache: Cache[String, Option[Any]] =
    Scaffeine()
      .maximumSize(localCacheSize)
      .build[String, Option[Any]]()

  private def abbreviateKey(key: String): String =
    if (key.length <= Config.memcached.keySize) key
    else key.substring(0, keySize - 1)

  private def withAbbreviatedKey[T](key: String)(body: String => T): T =
    body(abbreviateKey(key))

  def delete(key: String): Unit =
    if (cacheEnabled) withAbbreviatedKey(key)(client.delete)

  def setValue[T](key: String, value: T, ttlSeconds: Int = 0): Unit =
    withAbbreviatedKey(key) { key =>
      logger.debug(s"Setting Key: $key with TTL $ttlSeconds")
      val f = Future {
        client.set(key, ttlSeconds, value)
      }

      f.onFailure {
        case e =>
          logger.error(s"Error ${e.getMessage}")
          sendError(e)
      }
    }

  // Suppress asInstanceOf warning because we can't pattern match on the returned type since it's
  // eliminated by type erasure
  @SuppressWarnings(Array("AsInstanceOf"))
  @tailrec
  final def localGetOrElse[CachedType](
      cacheKey: String,
      expensiveOperation: => Future[Option[CachedType]],
      doCache: Boolean = true,
      depth: Int = 0)(
      fallbackFunction: (String,
                         => Future[Option[CachedType]],
                         Boolean) => Future[Option[CachedType]]
  ): Future[Option[CachedType]] = {
    // in order not to break tailrec
    val key = abbreviateKey(cacheKey)

    def fallback: Future[Option[CachedType]] = {
      // Signal to other cache reads that the operation in already in progress
      localCache.put(key, Some("AWAIT"))
      // Use the fallback function to retrieve the value and cache it
      val fallbackFuture: Future[Option[CachedType]] =
        fallbackFunction(key, expensiveOperation, doCache)
      fallbackFuture.onComplete {
        case Success(cachedValueO) =>
          localCache.put(key, cachedValueO)
        case Failure(e) =>
          sendError(RfStackTrace(e))
          logger.error(s"Cache set error at local cache: ${RfStackTrace(e)}")
      }
      fallbackFuture
    }

    localCache.getIfPresent(key) match {
      // The requested key is in the local cache
      case Some(cachedValueO) =>
        cachedValueO match {
          // The requested key is already being computed, try again
          case Some("AWAIT") if depth < maxRetryDepth =>
            Thread.sleep(retrySleepMillis)
            localGetOrElse(key, expensiveOperation, doCache, depth + 1)(
              fallbackFunction)
          case Some(cachedValue) if depth < maxRetryDepth =>
            logger.debug(s"Local Cache Hit: $key")
            Future.successful(Some(cachedValue.asInstanceOf[CachedType]))
          case _ =>
            fallback
        }
      // The requested key is not present in the local cache, so do the else function
      case _ =>
        // Load the local cache with the result of the else function
        fallback
    }
  }

  // Suppress asInstanceOf warning because we can't pattern match on the returned type since it's
  // eliminated by type erasure
  @SuppressWarnings(Array("AsInstanceOf"))
  def getOrElseUpdateMemcached[CachedType](
      cacheKey: String,
      expensiveOperation: => Future[Option[CachedType]],
      doCache: Boolean = true
  ): Future[Option[CachedType]] = withAbbreviatedKey(cacheKey) { cacheKey =>
    val futureCached = Future { client.asyncGet(cacheKey).get() }
    futureCached.flatMap(
      {
        case null =>
          logger.debug(s"Cache Miss: $cacheKey")
          val futureCached: Future[Option[CachedType]] = expensiveOperation
          futureCached.onComplete {
            case Success(cachedValue) =>
              cachedValue match {
                case Some(v) =>
                  if (doCache) {
                    setValue(cacheKey, cachedValue)
                  }
                case None =>
                  if (doCache) {
                    setValue(cacheKey, cachedValue, ttlSeconds = 300)
                  }
              }
            case Failure(e) =>
              sendError(RfStackTrace(e))
              logger.error(s"Cache Set Error: ${RfStackTrace(e)}")
          }
          futureCached
        case o =>
          logger.debug(s"Cache Hit: $cacheKey")
          Future.successful(o.asInstanceOf[Option[CachedType]])
      }
    )
  }

  def getOrElseUpdate[CachedType](
      cacheKey: String,
      expensiveOperation: => Future[Option[CachedType]],
      doCache: Boolean = true
  ): Future[Option[CachedType]] = withAbbreviatedKey(cacheKey) { cacheKey =>
    (doCache, cacheEnabled, localCacheEnabled) match {
      case (true, true, true) =>
        localGetOrElse[CachedType](cacheKey, expensiveOperation, doCache)(
          getOrElseUpdateMemcached[CachedType])
      case (true, true, false) =>
        getOrElseUpdateMemcached[CachedType](cacheKey,
                                             expensiveOperation,
                                             doCache)
      case _ => expensiveOperation
    }
  }

  def caching[T](cacheKey: String, doCache: Boolean = true)(
      mappingFunction: => Future[Option[T]]
  ): Future[Option[T]] = {
    getOrElseUpdate[T](cacheKey, mappingFunction, doCache)
  }

  def cachingOptionT[T](cacheKey: String, doCache: Boolean = true)(
      mappingFunction: => OptionT[Future, T]
  ): OptionT[Future, T] = {
    val futureOption =
      getOrElseUpdate[T](cacheKey, mappingFunction.value, doCache)
    OptionT(futureOption)
  }

}
