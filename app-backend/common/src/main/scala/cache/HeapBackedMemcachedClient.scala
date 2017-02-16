package com.azavea.rf.common.cache

import com.github.blemale.scaffeine.{ Cache => ScaffeineCache, Scaffeine }
import net.spy.memcached._

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.matching._


/**
  * Wraps a MemcachedClient to provide a local cache which stores Future results in case
  *  the call has recently been made and could otherwise result in a race condition.
  *
  *  @note Each instance of this wrapper will have its own instance of a Caffeine
  *         cache (a guava-inspired java library).
  */
class HeapBackedMemcachedClient[CachedType](
  client: MemcachedClient,
  options: HeapBackedMemcachedClient.Options = HeapBackedMemcachedClient.Options()) {

  /** The caffeine cache (on heap) which prevents race conditions */
  val onHeapCache: ScaffeineCache[String, Future[CachedType]] =
    Scaffeine()
      .recordStats()
      .expireAfterAccess(options.ttl)
      .maximumSize(options.maxSize)
      .build[String, Future[CachedType]]()

  /**
    * The primary means of interaction with this class: pass as key and
    *  the costly option caching prevents duplication of.
    */
  def caching(cacheKey: String)(expensiveOperation: String => Future[CachedType])(implicit ec: ExecutionContext): Future[CachedType] = {
    val sanitizedKey = HeapBackedMemcachedClient.sanitizeKey(cacheKey)
    val futureCached: Future[CachedType] =
      onHeapCache.get(sanitizedKey, { cKey: String => client.getOrElseUpdate[CachedType](cKey, expensiveOperation, options.ttl) })
    onHeapCache.put(sanitizedKey, futureCached)
    futureCached
  }
}

object HeapBackedMemcachedClient {
  case class Options(ttl: FiniteDuration = 2.seconds, maxSize: Int = 500)

  def apply[CachedType](client: MemcachedClient, options: Options = Options()) =
    new HeapBackedMemcachedClient[CachedType](client, options)

  /** This key sanitizer replaces whitespace with '_' and throws in case of control characters */
  def sanitizeKey(key: String): String = {
    val blacklist = "[^\u0020-\u007e]".r
    assert(key.length <= 250, s"Keys of length 250 or greater are not allowed; key provided has length of ${key.length}")
    assert(blacklist.findFirstIn(key) match {
      case Some(char) => false
      case None => true
    } , s"Invalid use of control character ( ${blacklist.findFirstIn(key).get} ) detected in key")
    val spaces = "[ \n\t\r]".r
    spaces.replaceAllIn(key, "_")
  }

}

