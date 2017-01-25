package com.azavea.rf.tile

import com.github.benmanes.caffeine.cache.Caffeine
import geotrellis.raster._
import geotrellis.raster.histogram.Histogram
import geotrellis.spark._
import geotrellis.raster.io._
import geotrellis.spark.io._
import geotrellis.spark.io.s3.{S3AttributeStore, S3ValueReader}
import scala.concurrent._
import java.util.concurrent.{Executors, TimeUnit}

import scala.util._
import scala.concurrent.ExecutionContext.Implicits.global
import spray.json.DefaultJsonProtocol._
import scalacache._

import scalacache.caffeine.CaffeineCache
import scalacache.memcached.MemcachedCache
import com.github.benmanes.caffeine.cache._

import scalacache.serialization.InMemoryRepr

/**
  * ValueReaders need to read layer metadata in order to know how to decode (x/y) queries into resource reads.
  * In this case it requires reading JSON files from S3, which are cached in the reader.
  * Naturally we want to cache this access to prevent every tile request from re-fetching layer metadata.
  * Same logic applies to other layer attributes like layer Histogram.
  *
  * Things that are cheap to construct but contain internal state we want to re-use use LoadingCache.
  * things that require time to generate, usually a network fetch, use AsyncLoadingCache
  */
object LayerCache extends Config {
  val blockingExecutionContext =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(64))

  implicit val memoryCache: ScalaCache[InMemoryRepr] = {
    val underlyingCaffeineCache =
      Caffeine.newBuilder()
        .maximumSize(cacheSize)
        .expireAfterAccess(cacheExpiration.toMillis, TimeUnit.MILLISECONDS)
        .build[String, Object]
    ScalaCache(CaffeineCache(underlyingCaffeineCache))
  }

  // TODO: Make a scalacache Codec using on Kryo
  implicit val memcached: ScalaCache[Array[Byte]] = {
    import net.spy.memcached._
    import java.net.InetSocketAddress
    val client = new MemcachedClient(new InetSocketAddress(memcachedHost, memcachedPort))
    ScalaCache(MemcachedCache(client))
  }

  def attributeStore(bucket: String, prefix: String): Future[S3AttributeStore] =
    caching[S3AttributeStore, InMemoryRepr](s"store-$bucket-$prefix"){
      Future.successful(S3AttributeStore(bucket, prefix))
    }

  def attributeStore(prefix: String): Future[S3AttributeStore] =
    attributeStore(defaultBucket, prefix)

  def maybeTile(id: RfLayerId, zoom: Int, key: SpatialKey): Future[Option[MultibandTile]] =
    caching[Option[MultibandTile], Array[Byte]](s"tile-$id-$zoom-$key") {
      for (store <- attributeStore(defaultBucket, id.prefix)) yield {
        val reader = new S3ValueReader(store).reader[SpatialKey, MultibandTile](id.catalogId(zoom))
        Try(reader.read(key)) match {
          // Only cache failures through failed query
          case Success(tile) => Some(tile)
          case Failure(e: ValueNotFoundError) => None
          case Failure(e) => throw e
        }
      }
    }

  def bandHistogram(id: RfLayerId, zoom: Int): Future[Array[Histogram[Double]]] =
    caching[Array[Histogram[Double]], Array[Byte]]("histogram", id, zoom) {
      for (store <- attributeStore(defaultBucket, id.prefix)) yield
        store.read[Array[Histogram[Double]]](id.catalogId(0), "histogram")
    }
}
