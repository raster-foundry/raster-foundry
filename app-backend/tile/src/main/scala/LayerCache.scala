package com.azavea.rf.tile

import com.azavea.rf.tile.tool.TileSources
import com.azavea.rf.datamodel.Tool
import com.azavea.rf.tool.op.Interpreter
import com.azavea.rf.tool.ast.MapAlgebraAST
import com.azavea.rf.common.cache._
import com.azavea.rf.common.cache.kryo.KryoMemcachedClient
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables._

import geotrellis.raster.op.Op
import geotrellis.raster._
import geotrellis.raster.histogram._
import geotrellis.spark._
import geotrellis.raster.io._
import geotrellis.spark.io._
import geotrellis.spark.io.s3.{S3InputFormat, S3AttributeStore, S3CollectionLayerReader, S3ValueReader}
import com.github.blemale.scaffeine.{Scaffeine, Cache => ScaffeineCache}
import geotrellis.vector.Extent
import com.typesafe.scalalogging.LazyLogging
import spray.json.DefaultJsonProtocol._

import java.security.InvalidParameterException
import java.util.UUID
import scala.concurrent._
import scala.concurrent.duration._
import scala.util._
import cats.data._
import cats.implicits._


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
  private val histogramCache = HeapBackedMemcachedClient(memcachedClient)
  private val tileCache = HeapBackedMemcachedClient(memcachedClient)

  private val layerUriCache: ScaffeineCache[UUID, OptionT[Future, String]] =
    Scaffeine()
      .recordStats()
      .expireAfterAccess(5.minutes)
      .maximumSize(500)
      .build[UUID, OptionT[Future, String]]

  private val attributeStoreCache: ScaffeineCache[UUID, OptionT[Future, (AttributeStore, Map[String, Int])]] =
    Scaffeine()
      .recordStats()
      .expireAfterAccess(5.minutes)
      .maximumSize(500)
      .build[UUID, OptionT[Future, (AttributeStore, Map[String, Int])]]

  def layerUri(layerId: UUID)(implicit ec: ExecutionContext): OptionT[Future, String] =
    layerUriCache.get(layerId, _ => blocking {
      OptionT(Scenes.getSceneForCaching(layerId).map(_.flatMap(_.ingestLocation)))
    })

  def attributeStoreForLayer(layerId: UUID)(implicit ec: ExecutionContext): OptionT[Future, (AttributeStore, Map[String, Int])] =
    attributeStoreCache.get(layerId, _ =>
      layerUri(layerId).mapFilter { catalogUri =>
        for (result <- S3InputFormat.S3UrlRx.findFirstMatchIn(catalogUri)) yield {
          val bucket = result.group("bucket")
          val prefix = result.group("prefix")
          // TODO: Decide if we should verify URI is valid. This may be a store that always fails to read

          val store = S3AttributeStore(bucket, prefix)
          val maxZooms: Map[String, Int] = blocking {
            store.layerIds.groupBy(_.name).mapValues(_.map(_.zoom).max)
          }
          (store, maxZooms)
        }
      }
    )

  def layerHistogram(layerId: UUID, zoom: Int): OptionT[Future, Array[Histogram[Double]]] =
    histogramCache.cachingOptionT(s"histogram-$layerId-$zoom") { implicit ec =>
      attributeStoreForLayer(layerId).map { case (store, _) => blocking {
        store.read[Array[Histogram[Double]]](LayerId(layerId.toString, 0), "histogram")
      }}
    }

  def modelLayerHistogram(toolId: UUID, nodeId: Option[UUID]): OptionT[Future, Histogram[Double]] =
    histogramCache.cachingOptionT(s"model-$toolId-$nodeId") { implicit ec =>
      val futureTool: Future[Tool.WithRelated] =
        Tools.getTool(toolId).map { _.getOrElse(throw new java.io.IOException(toolId.toString)) }

      OptionT(futureTool.flatMap { tool =>
        val ast = tool.definition.as[MapAlgebraAST] match {
          case Right(ast) => ast
          case Left(failure) => throw failure
        }
        val subAst: MapAlgebraAST = nodeId match {
          case Some(id) =>
            ast.find(id).getOrElse(throw new InvalidParameterException(s"AST has no node with the id $id"))
          case None => ast
        }

        Interpreter.interpretGlobal(subAst, TileSources.cachedGlobalSource).map({ validatedOp =>
          for {
            op <- validatedOp.toOption
            tile <- op.toTile(DoubleCellType)
          } yield StreamingHistogram.fromTile(tile)
        })
      })
    }


  def layerTile(layerId: UUID, zoom: Int, key: SpatialKey): OptionT[Future, MultibandTile] =
    tileCache.cachingOptionT(s"tile-$layerId-$zoom-${key.col}-${key.row}") { implicit ec =>
      attributeStoreForLayer(layerId).mapFilter { case (store, _) =>
        val reader = new S3ValueReader(store).reader[SpatialKey, MultibandTile](LayerId(layerId.toString, zoom))
        blocking {
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

  def layerTileForExtent(layerId: UUID, zoom: Int, extent: Extent): OptionT[Future, MultibandTile] =
    tileCache.cachingOptionT(s"extent-tile-$layerId-$zoom-$extent") { implicit ec =>
      attributeStoreForLayer(layerId).mapFilter { case (store, _) =>
        blocking {
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
