package com.rasterfoundry.database.util

import java.net.InetSocketAddress
import java.util.concurrent.Executors

import cats._
import cats.implicits._
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.rasterfoundry.common.{
  BacksplashConnectionFactory,
  Config,
  SceneToLayerWithSceneType
}
import com.rasterfoundry.datamodel._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.raster.histogram.Histogram
import net.spy.memcached.MemcachedClient
import scalacache._
import scalacache.memcached.MemcachedCache

import scala.collection.JavaConverters._
import scalacache.serialization.circe._

import scala.concurrent.duration.Duration

object Cache extends LazyLogging {

  def getOptionCache[F[_]: Monad, T](
      cacheKey: String,
      ttl: Option[Duration] = None)(f: => F[Option[T]])(
      implicit cache: Cache[T],
      mode: Mode[F]): F[Option[T]] = {
    val cacheValue: F[Option[T]] = get(cacheKey)
    cacheValue.flatMap {
      case Some(t) =>
        logger.debug(s"Cache Hit for Key: ${cacheKey}")
        Applicative[F].pure(Some(t))
      case _ =>
        logger.debug(s"Cache Miss for Key: $cacheKey")
        f.flatMap {
          case Some(selectT) =>
            logger.debug(s"Inserting Key ($cacheKey) into Cache")
            put(cacheKey)(selectT, ttl).map(_ => Some(selectT): Option[T])
          case _ => Applicative[F].pure(None)
        }
    }
  }

  lazy val es = Executors.newCachedThreadPool(
    new ThreadFactoryBuilder().setNameFormat("cache-%d").build())
  val address =
    new InetSocketAddress(Config.memcached.host, Config.memcached.port)
  val memcachedClient =
    new MemcachedClient(new BacksplashConnectionFactory(es),
                        List(address).asJava)

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

  object MosaicDefinitionCache {
    import scalacache.serialization.binary._
    implicit val mosaicDefinitionCache
      : Cache[List[SceneToLayerWithSceneType]] = {
      MemcachedCache[List[SceneToLayerWithSceneType]](memcachedClient)
    }
  }

  object DatasourceCache {
    implicit val datasourceCache: Cache[Datasource] = {
      MemcachedCache[Datasource](memcachedClient)
    }
  }

  object SceneCache {
    implicit val sceneCache: Cache[Scene] = {
      MemcachedCache[Scene](memcachedClient)
    }
  }

  object HistogramCache {
    import scalacache.serialization.binary._
    implicit val histogramCache: Cache[Array[Histogram[Double]]] = {
      MemcachedCache[Array[Histogram[Double]]](memcachedClient)
    }
  }
}
