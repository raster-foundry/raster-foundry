package com.rasterfoundry.backsplash

import com.typesafe.scalalogging.LazyLogging
import geotrellis.contrib.vlm.geotiff.GeoTiffRasterSource
import geotrellis.raster.MultibandTile
import geotrellis.raster.histogram.Histogram
import scalacache._
import scalacache.caffeine._
import scalacache.memcached._
import scalacache.memoization._
import scalacache.serialization.binary._
import net.spy.memcached._
import java.net.InetSocketAddress
import scala.collection.JavaConverters._

object Cache extends LazyLogging {

  class BacksplashConnectionFactory extends DefaultConnectionFactory() {
    override def getClientMode: ClientMode = Config.cache.memcachedClientMode

    override def getOperationTimeout: Long =
      Config.cache.memcachedTimeoutMilliseconds
  }

  val tileCache: Cache[Option[MultibandTile]] = {

    val address = new InetSocketAddress(Config.cache.memcachedHost,
                                        Config.cache.memcachedPort)
    val memcachedClient =
      new MemcachedClient(new BacksplashConnectionFactory, List(address).asJava)

    implicit val cacheConfig: CacheConfig = CacheConfig(
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

  val rasterSourceCache: Cache[GeoTiffRasterSource] =
    CaffeineCache[GeoTiffRasterSource]
  val rasterSourceCacheFlags: Flags = Flags(
    Config.cache.rasterSourceCacheEnable,
    Config.cache.rasterSourceCacheEnable)
  logger.info(s"Raster Source Cache Status: ${rasterSourceCacheFlags}")

}
