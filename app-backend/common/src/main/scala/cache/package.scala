package com.azavea.rf.common

import net.spy.memcached._

import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent._
import scala.concurrent.duration._
import scala.util._
import scala.concurrent.ExecutionContext.Implicits.global


package object cache {
  implicit class MemcachedClientMethods(client: MemcachedClient) {
    def getOrElseUpdate[CachedType](cacheKey: String, expensiveOperation: String => Future[CachedType], ttl: Duration)(implicit ec: ExecutionContext): Future[CachedType] = {
      val futureCached = Future { client.asyncGet(cacheKey).get() }
      futureCached.flatMap({ value =>
        if (value != null) { // cache hit
          Future { value.asInstanceOf[CachedType] }
        } else { // cache miss
          val futureCached: Future[CachedType] = expensiveOperation(cacheKey)
          futureCached.foreach({ cachedValue => client.set(cacheKey, ttl.toSeconds.toInt, cachedValue) })
          futureCached
        }
      })
    }
  }
}
