package com.azavea.rf.tile

import com.azavea.rf.database.Database
import com.azavea.rf.common.cache._
import com.azavea.rf.common.cache.kryo.KryoMemcachedClient
import com.azavea.rf.ingest.util.S3.S3UrlRx
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.Scenes
import geotrellis.raster._
import geotrellis.raster.histogram.Histogram
import geotrellis.spark._
import geotrellis.raster.io._
import geotrellis.spark.io._
import geotrellis.spark.io.s3.{S3AttributeStore, S3CollectionLayerReader, S3ValueReader}
import com.github.blemale.scaffeine.{Scaffeine, Cache => ScaffeineCache}
import geotrellis.vector.Extent

import spray.json.DefaultJsonProtocol._

import java.util.UUID
import scala.concurrent._
import scala.concurrent.duration._
import scala.util._
import cats.data._
import cats.implicits._

import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * ValueReaders need to read layer metadata in order to know how to decode (x/y) queries into resource reads.
  * In this case it requires reading JSON files from S3, which are cached in the reader.
  * Naturally we want to cache this access to prevent every tile request from re-fetching layer metadata.
  * Same logic applies to other layer attributes like layer Histogram.
  *
  * Things that are cheap to construct but contain internal state we want to re-use use LoadingCache.
  * things that require time to generate, usually a network fetch, use AsyncLoadingCache
  */
object LayerCache extends Config with LazyLogging {
  implicit val database = Database.DEFAULT

  val memcachedClient = KryoMemcachedClient.DEFAULT

  /** The caffeine cache to use for attribute stores */
  private val attributeStoreCache: ScaffeineCache[UUID, OptionT[Future, AttributeStore]] =
    Scaffeine()
      .recordStats()
      .expireAfterAccess(5.minutes)
      .maximumSize(500)
      .build[UUID, OptionT[Future, AttributeStore]]

  private val histogramCache = HeapBackedMemcachedClient(memcachedClient)
  private val tileCache = HeapBackedMemcachedClient(memcachedClient)
  private val layerLocationCache = HeapBackedMemcachedClient(memcachedClient)

  def layerUri(layerId: UUID): OptionT[Future, String] = OptionT {
    layerLocationCache.caching(s"layer-uri-${layerId.toString}") { _ =>
      Scenes.getScene(layerId).map(_.flatMap(_.ingestLocation))
    }
  }

  def attributeStoreForLayer(layerId: UUID): OptionT[Future, AttributeStore] = {
    attributeStoreCache.get(layerId, _ =>
      layerUri(layerId).mapFilter { catalogUri =>
        for (result <- S3UrlRx.findFirstMatchIn(catalogUri)) yield {
          val bucket = result.group("bucket")
          val prefix = result.group("prefix")
          // TODO: Decide if we should verify URI is valid. This may be a store that always fails to read
          S3AttributeStore(bucket, prefix)
        }
      }
    )
  }

  def layerHistogram(layerId: UUID, zoom: Int): OptionT[Future, Array[Histogram[Double]]] = {
    histogramCache.cachingOptionT(s"histogram-$layerId-$zoom") { implicit ec =>
      attributeStoreForLayer(layerId).map { store =>
        store.read[Array[Histogram[Double]]](LayerId(layerId.toString, 0), "histogram")
      }
    }
  }

  def layerTile(layerId: UUID, zoom: Int, key: SpatialKey): OptionT[Future, MultibandTile] = {
    tileCache.cachingOptionT(s"tile-$layerId-$zoom-${key.col}-${key.row}") { implicit ec =>
      attributeStoreForLayer(layerId).mapFilter { store =>
        val reader = new S3ValueReader(store).reader[SpatialKey, MultibandTile](LayerId(layerId.toString, zoom))
        Try {
          reader.read(key)
        } match {
          case Success(tile) => Option(tile)
          case Failure(e: ValueNotFoundError) => None
          case Failure(e) =>
            logger.error(s"Reading layer $layerId at $key: ${e.getMessage}")
            None
        }
      }
    }
  }

  def layerTileForExtent(layerId: UUID, zoom: Int, extent: Extent): OptionT[Future, MultibandTile] = {
    tileCache.cachingOptionT(s"extent-tile-$layerId-$zoom-$extent") { implicit ec =>
      attributeStoreForLayer(layerId).mapFilter { store =>
        Try {
          S3CollectionLayerReader(store)
            .query[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](LayerId(layerId.toString, zoom))
            .where(Intersects(extent))
            .result
            .stitch
            .crop(extent)
            .tile
        } match {
          case Success(tile) => Option(tile)
          case Failure(e) =>
            logger.error(s"Query layer $layerId at zoom $zoom for $extent: ${e.getMessage}")
            None
        }
      }
    }
  }
}
