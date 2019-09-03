package com.rasterfoundry.database.util

import java.net.InetSocketAddress

import com.rasterfoundry.common.{BacksplashConnectionFactory, Config}
import com.rasterfoundry.datamodel._
import com.typesafe.scalalogging.LazyLogging
import doobie.ConnectionIO
import doobie.implicits.AsyncConnectionIO
import net.spy.memcached.MemcachedClient
import scalacache._
import scalacache.memcached.MemcachedCache

import scala.collection.JavaConverters._
import scalacache.serialization.circe._

import scala.concurrent.duration.Duration

object Cache extends LazyLogging {

  def getOptionCache[T](cacheKey: String, ttl: Option[Duration] = None)(
      f: => ConnectionIO[Option[T]])(
      implicit cache: Cache[T],
      mode: Mode[ConnectionIO]): ConnectionIO[Option[T]] = {
    val cacheValue: ConnectionIO[Option[T]] = get(cacheKey)
    cacheValue.flatMap {
      case Some(t) =>
        logger.debug(s"Cache Hit for Key: ${cacheKey}")
        AsyncConnectionIO.pure[Option[T]](Some(t))
      case _ =>
        logger.debug(s"Cache Miss for Key: $cacheKey")
        f.flatMap {
          case Some(selectT) =>
            logger.debug(s"Inserting Key ($cacheKey) into Cache")
            put(cacheKey)(selectT, ttl).map(_ => Some(selectT): Option[T])
          case _ => AsyncConnectionIO.pure[Option[T]](None)
        }
    }
  }

  val address =
    new InetSocketAddress(Config.memcached.host, Config.memcached.port)
  val memcachedClient =
    new MemcachedClient(new BacksplashConnectionFactory, List(address).asJava)

  object ProjectCache {
    implicit val projectCache: Cache[Project] = {
      MemcachedCache[Project](memcachedClient)
    }
  }

  object ProjectLayerCache {
    implicit val projectLayerCache: Cache[ProjectLayer] = {
      MemcachedCache[ProjectLayer](memcachedClient)
    }
  }

  object UserCache {
    implicit val userCache: Cache[User] = {
      MemcachedCache[User](memcachedClient)
    }
  }
}
