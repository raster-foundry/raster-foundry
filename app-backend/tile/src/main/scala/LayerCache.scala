package com.azavea.rf.tile

import com.azavea.rf.database.Database
import com.azavea.rf.common.cache._
import com.azavea.rf.common.cache.kryo.KryoMemcachedClient
import com.github.benmanes.caffeine.cache.Caffeine
import geotrellis.raster._
import geotrellis.raster.histogram.Histogram
import geotrellis.spark._
import geotrellis.raster.io._
import geotrellis.spark.io._
import geotrellis.spark.io.s3.{S3AttributeStore, S3ValueReader, S3CollectionLayerReader}
import com.github.blemale.scaffeine.{ Cache => ScaffeineCache, Scaffeine }
import geotrellis.vector.Extent
import com.github.benmanes.caffeine.cache._
import com.github.benmanes.caffeine.cache.Caffeine
import spray.json.DefaultJsonProtocol._
import net.spy.memcached._
import java.net.InetSocketAddress

import scala.concurrent._
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util._
import java.util.concurrent.{Executors, TimeUnit}


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
  implicit val database = Database.DEFAULT

  val memcachedClient = KryoMemcachedClient.DEFAULT

  /** The caffeine cache to use for attribute stores */
  val attributeStoreCache: ScaffeineCache[String, Future[S3AttributeStore]] =
    Scaffeine()
      .recordStats()
      .expireAfterAccess(5.minutes)
      .maximumSize(500)
      .build[String, Future[S3AttributeStore]]()

  /**
    * The primary means of interaction with this class: pass as key and
    *  the costly option caching prevents duplication of.
    */
  def attributeStore(bucket: String, prefix: Option[String]): Future[S3AttributeStore] = {
    val cacheKey = HeapBackedMemcachedClient.sanitizeKey(s"store-$bucket-$prefix")
    attributeStoreCache.get(cacheKey, { cacheKey =>
      prefix match {
        case Some(prefixStr) => Future.successful(S3AttributeStore(bucket, prefixStr))
        case None => Future.failed(new LayerIOError("Scene has no ingest location"))
      }
    })
  }

  def attributeStore(prefix: Option[String]): Future[S3AttributeStore] =
    attributeStore(defaultBucket, prefix)

  val tileCache = HeapBackedMemcachedClient[Option[MultibandTile]](memcachedClient)
  def maybeRenderExtent(id: RfLayerId, zoom: Int, extent: Extent): Future[Option[MultibandTile]] =
    tileCache.caching(s"rendered-extent-$id-$zoom-$extent") { implicit ec =>
      for {
        prefix <- id.prefix
        store <- attributeStore(defaultBucket, prefix)
      } yield {
        val reader = new S3ValueReader(store).reader[SpatialKey, MultibandTile](id.catalogId(zoom))
        val query = S3CollectionLayerReader(store)
          .query[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](id.catalogId(zoom))
        val tile = query.where(Intersects(extent))
          .result
          .stitch
          .crop(extent)
          .tile
        tile match {
          case tile: MultibandTile => Some(tile)
          case _ => None
        }
      }
    }

  def maybeTile(id: RfLayerId, zoom: Int, key: SpatialKey): Future[Option[MultibandTile]] =
    tileCache.caching(s"tile-$id-$zoom-$key") { implicit ec =>
      for {
        prefix <- id.prefix
        store <- attributeStore(defaultBucket, prefix)
      } yield {
        val reader = new S3ValueReader(store).reader[SpatialKey, MultibandTile](id.catalogId(zoom))
        Try(reader.read(key)) match {
          // Only cache failures through failed query
          case Success(tile) => Some(tile)
          case Failure(e: ValueNotFoundError) => None
          case Failure(e) => throw e
        }
      }
    }


  val histogramCache = HeapBackedMemcachedClient[Array[Histogram[Double]]](memcachedClient)
  def bandHistogram(id: RfLayerId, zoom: Int): Future[Array[Histogram[Double]]] =
    histogramCache.caching(s"histogram-$id-$zoom") { implicit ec =>
      for {
        prefix <- id.prefix
        store <- attributeStore(defaultBucket, prefix)
      } yield store.read[Array[Histogram[Double]]](id.catalogId(0), "histogram")
    }
}
