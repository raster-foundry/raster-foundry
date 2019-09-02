package com.rasterfoundry.backsplash

import com.typesafe.scalalogging.LazyLogging
import geotrellis.contrib.vlm.RasterSource
import geotrellis.raster.MultibandTile
import geotrellis.raster.histogram.Histogram
import geotrellis.vector.{MultiPolygon, Point}
import org.locationtech.spatial4j.io.GeohashUtils
import scalacache._
import scalacache.caffeine._
import scalacache.memcached._
import scalacache.memoization._
import scalacache.serialization.binary._
import net.spy.memcached._
import java.net.InetSocketAddress

import com.rasterfoundry.common.BacksplashConnectionFactory

import scala.collection.JavaConverters._

object Cache extends LazyLogging {

  class BacksplashCacheKeyBuilder extends CacheKeyBuilder {
    def toCacheKey(parts: Seq[Any]): String = {
      val strs = parts flatMap {
        // Can't match on Option[MultiPolygon] directly since the type parameter
        // gets eliminated
        case opt: Option[_] =>
          opt flatMap {
            case mp: MultiPolygon =>
              mp.centroid.as[Point] map { point =>
                GeohashUtils.encodeLatLon(point.x, point.y, 15)
              }
            case x => Some(x.toString)
          }
        case part => Some(part.toString)
      }
      strs.mkString(":")
    }

    def stringToCacheKey(key: String) = key
  }

  val tileCache: Cache[Option[MultibandTile]] = {

    val address = new InetSocketAddress(Config.cache.memcachedHost,
                                        Config.cache.memcachedPort)
    val memcachedClient =
      new MemcachedClient(new BacksplashConnectionFactory, List(address).asJava)

    implicit val cacheConfig: CacheConfig = CacheConfig(
      cacheKeyBuilder = new BacksplashCacheKeyBuilder,
      memoization = MemoizationConfig(
        MethodCallToStringConverter.includeClassConstructorParams)
    )

    MemcachedCache[Option[MultibandTile]](memcachedClient)
  }

  val tileCacheFlags =
    Flags(Config.cache.tileCacheEnable, Config.cache.tileCacheEnable)
  logger.info(s"Tile Cache Status: ${tileCacheFlags}")

  val histCache: Cache[Array[Histogram[Double]]] =
    CaffeineCache[Array[Histogram[Double]]]

  val histCacheFlags =
    Flags(Config.cache.histogramCacheEnable, Config.cache.histogramCacheEnable)
  logger.info(s"Histogram Cache Status: ${histCacheFlags}")

  val rasterSourceCache: Cache[RasterSource] = {
    implicit val cacheConfig: CacheConfig = CacheConfig(
      memoization = MemoizationConfig(
        MethodCallToStringConverter.includeClassConstructorParams)
    )
    CaffeineCache[RasterSource]
  }

  val rasterSourceCacheFlags: Flags = Flags(
    Config.cache.rasterSourceCacheEnable,
    Config.cache.rasterSourceCacheEnable)
  logger.info(s"Raster Source Cache Status: ${rasterSourceCacheFlags}")

}
