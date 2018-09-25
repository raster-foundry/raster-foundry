package com.azavea.rf.common.cache

import com.typesafe.scalalogging.LazyLogging
import net.spy.memcached._

import scala.concurrent._
import scala.concurrent.duration._
@SuppressWarnings(Array("AsInstanceOf", "CatchException"))
class MemcachedClientMethods(client: MemcachedClient) extends LazyLogging {
  def getOrElseUpdate[CachedType](
      cacheKey: String,
      expensiveOperation: => Future[CachedType],
      ttl: Duration)(implicit ec: ExecutionContext): Future[CachedType] =
    blocking {
      val futureCached = Future { client.asyncGet(cacheKey).get() }
      futureCached.flatMap({ value =>
        if (value != null) { // cache hit
          logger.debug(s"Cache Hit: $cacheKey - $value")
          Future.successful(value.asInstanceOf[CachedType])
        } else { // cache miss
          logger.debug(s"Cache Miss: $cacheKey")
          val futureCached: Future[CachedType] = expensiveOperation
          futureCached.foreach { cachedValue =>
            try {
              client.set(cacheKey, ttl.toSeconds.toInt, cachedValue)
              logger.debug(s"Cache Set: $cacheKey - $cachedValue")
            } catch {
              case e: Exception =>
                logger.debug(s"Cache Set Error: ${e.getMessage}")
            }
          }

          futureCached
        }
      })
    }
}
