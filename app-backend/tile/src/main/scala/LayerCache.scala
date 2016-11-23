package com.azavea.rf.tile

import geotrellis.raster._
import geotrellis.raster.histogram.Histogram
import geotrellis.spark._
import geotrellis.raster.io._
import geotrellis.spark.io._
import geotrellis.spark.io.s3.{S3AttributeStore, S3ValueReader}
import scala.concurrent._
import java.util.concurrent.Executors
import scala.util._
import com.github.blemale.scaffeine.{ AsyncLoadingCache, Scaffeine, LoadingCache }
import scala.concurrent.ExecutionContext.Implicits.global
import spray.json.DefaultJsonProtocol._

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
  /** Cache AttributeStores.
    * This is not Async cache as per usual because constructing S3AttributeStore is cheap.
    * However it maintains an internal cache of layer attributes that are reused when reading tiles.
    */
  val cacheAttributeStore: LoadingCache[(String, String), S3AttributeStore] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(cacheExpiration)
      .maximumSize(cacheSize)
      .build { case (bucket: String, prefix: String) => S3AttributeStore(bucket, prefix) }

  val cacheHistogram: AsyncLoadingCache[(RfLayerId, Int), Array[Histogram[Double]]] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(cacheExpiration)
      .maximumSize(cacheSize)
      .buildAsyncFuture { case (id, zoom: Int) =>
        Future { S3AttributeStore(defaultBucket, id.prefix).read[Array[Histogram[Double]]](id.catalogId(zoom), "histogram") }
      }

  val cacheReaders: LoadingCache[(RfLayerId, Int), Reader[SpatialKey, MultibandTile]] =
    Scaffeine()
      .expireAfterWrite(cacheExpiration)
      .maximumSize(cacheSize)
      .build { case (id, zoom) =>
        new S3ValueReader(attributeStore(defaultBucket, id.prefix)).reader[SpatialKey, MultibandTile](id.catalogId(zoom))
      }

  val blockingExecutionContext =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(64))

  def attributeStore(bucket: String, prefix: String): S3AttributeStore =
    cacheAttributeStore.get((bucket, prefix))

  def attributeStore(prefix: String): S3AttributeStore =
    attributeStore(defaultBucket, prefix)

  val cacheMaybeTile: AsyncLoadingCache[(RfLayerId, Int, SpatialKey), Option[MultibandTile]] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(cacheExpiration)
      .maximumSize(cacheSize)
      .buildAsyncFuture { case (id: RfLayerId, zoom, key: SpatialKey) =>
        Future {
          Try(cacheReaders.get((id, zoom)).read(key))  match {
            // Only cache failures through failed query
            case Success(tile) => Some(tile)
            case Failure(e: TileNotFoundError) => None
            case Failure(e) => throw e
          }
        }(blockingExecutionContext)
    }

  def maybeTile(id: RfLayerId, zoom: Int, key: SpatialKey): Future[Option[MultibandTile]] =
    cacheMaybeTile.get((id, zoom, key))

  def bandHistogram(id: RfLayerId, zoom: Int): Future[Array[Histogram[Double]]] =
    cacheHistogram.get((id, 0))
}
