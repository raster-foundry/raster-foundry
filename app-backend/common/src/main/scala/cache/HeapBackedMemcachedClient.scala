package com.azavea.rf.common.cache

import com.github.blemale.scaffeine.{Scaffeine, Cache => ScaffeineCache}
import net.spy.memcached._
import cats.data._
import scala.concurrent._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import java.util.concurrent.Executors

import com.azavea.rf.common.Config


/**
  * Wraps a MemcachedClient to provide a local cache which stores Future results in case
  *  the call has recently been made and could otherwise result in a cache race condition.
  *
  *  @note Each instance of this wrapper will have its own instance of a Caffeine
  *         cache (a guava-inspired java library).
  */
class HeapBackedMemcachedClient(
  client: MemcachedClient,
  options: HeapBackedMemcachedClient.Options = HeapBackedMemcachedClient.Options()) {

  import HeapBackedMemcachedClient._

  /**
    * Returns the value associated with `cacheKey` in the memcached, obtaining that value from
    * `mappingFunction` if necessary. This method provides a simple substitute for the
    * conventional "if cached, return; otherwise create, cache and return" pattern.
    *
    * `mappingFunction` is passed an `ExecutionContext` it is expected to use if it is a blocking function.
    * The best way to do this is to mark the parameter as explicit for the body of the function closure:
    *
    * {{{
    *   val memCache: HeapBackedMemcachedClient = ???
    *   def getTile(layer: String, zoom: Int): Future[Tile] =
    *     memCache.caching(s"tile-$layer-$zoom") { implicit ec =>
    *       // get the tile in a Future
    *     }
    * }}}
    *
    * @param cacheKey         key with which the specified value is to be associated
    * @param mappingFunction  the function to compute a value
    * @return the current (existing or computed) value associated with the specified key
    */
  def caching[T](cacheKey: String)(mappingFunction: ExecutionContext => Future[T]): Future[T] = {
    val sanitizedKey = HeapBackedMemcachedClient.sanitizeKey(cacheKey)
    onHeapCache.get(sanitizedKey, { key: String =>
      client.getOrElseUpdate[T](key, mappingFunction(options.ec), options.memcachedTTL)(options.ec)
    }).asInstanceOf[Future[T]]
  }

  def cachingOptionT[T](cacheKey: String)(mappingFunction: ExecutionContext => OptionT[Future, T]): OptionT[Future, T] = {
    val sanitizedKey = HeapBackedMemcachedClient.sanitizeKey(cacheKey)
    val futureOption = onHeapCache.get(sanitizedKey, { key: String =>
      client.getOrElseUpdate[Option[T]](key, mappingFunction(options.ec).value, options.memcachedTTL)(options.ec)
    }).asInstanceOf[Future[Option[T]]]
    OptionT(futureOption)
  }

}

object HeapBackedMemcachedClient {
  /** The default ExecutionContext to be used for memcached queries and passed to the function generating the value.
    * It is expected that such functions are most often going to involve blocking I/O.
    *
    * Separate execution context is desired in this cases because it will prevent thread contention between
    * these blocking functions and the default thread pool which services the HTTP requests.
    *
    * It is also important for the number of IO threads to be bounded because once the IO channel is saturated
    * each new request would otherwise continue spawning additional threads until the heap memory is exhausted.
    */
 lazy val defaultExecutionContext: ExecutionContext =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(Config.memcached.threads))

  case class Options(
    ec: ExecutionContext = defaultExecutionContext,
    heapTTL: FiniteDuration = Config.memcached.heapEntryTTL,
    memcachedTTL: FiniteDuration = 12.minutes,
    maxSize: Int = Config.memcached.heapMaxEntries
  )

  /** The caffeine cache (on heap) which prevents cache race conditions.
    * Entries on this cache are intended to live only long enough to satisfy requests from a "stampeding heard".
    */
  private val onHeapCache: ScaffeineCache[String, Future[Any]] =
    Scaffeine()
      .expireAfterAccess(Config.memcached.heapEntryTTL)
      .maximumSize(Config.memcached.heapMaxEntries)
      .build[String, Future[Any]]()


  def apply(client: MemcachedClient, options: Options = Options()) =
    new HeapBackedMemcachedClient(client, options)

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