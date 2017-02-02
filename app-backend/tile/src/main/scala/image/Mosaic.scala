package com.azavea.rf.tile.image

import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.ScenesToProjects
import com.azavea.rf.datamodel.MosaicDefinition

import com.github.blemale.scaffeine.{ Cache => ScaffCache, Scaffeine }
import scalacache.caffeine.CaffeineCache
import com.azavea.rf.tile._
import scalacache._
import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import java.util.UUID


case class TagWithTTL(tag: String, ttl: Duration)

object Mosaic {
  implicit val cache = LayerCache.memoryCache

  val memcachedClient = LayerCache.memcachedClient

  /** Cache the result of metadata queries that may have required walking up the pyramid to find suitable layers */
  def tileLayerMetadata(id: RfLayerId, zoom: Int)(implicit database: Database) =
      caching(s"mosaic-tlm-$id-$zoom") {
    def readMetadata(store: AttributeStore, tryZoom: Int): Option[(Int, TileLayerMetadata[SpatialKey])] =
      try {
        Some(tryZoom -> store.readMetadata[TileLayerMetadata[SpatialKey]](id.catalogId(tryZoom)))
      } catch {
        case e: AttributeNotFoundError if tryZoom > 0 => readMetadata(store, tryZoom - 1)
      }

    for {
      prefix <- id.prefix
      store <- LayerCache.attributeStore(prefix)
    } yield {
      readMetadata(store, zoom)
    }
  }

  // This cache of futures will allow for atomic calls to memcached (on one machine)
  val futureMosaicDefinitions: ScaffCache[String, Future[Option[MosaicDefinition]]] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(20.minute)
      .maximumSize(500)
      .build[String, Future[Option[MosaicDefinition]]]()

  def mosaicDefinition(projectId: UUID, tagttl: Option[TagWithTTL])(implicit db: Database) = {

    def fetch(cKey: String): Future[Option[MosaicDefinition]] = {
      memcachedClient
        .asyncGet(cKey)
        .asFuture[MosaicDefinition]
        .flatMap({ maybeMosaicDefinition =>
          Option(maybeMosaicDefinition) match {
            case Some(mosaicDefinition) => // cache hit
              Future {
                Option(mosaicDefinition)
              }
            case None =>         // cache miss
              val futureMaybeDefinition = ScenesToProjects.getMosaicDefinition(projectId)
              for {
                maybeDef <- futureMaybeDefinition
                definition <- maybeDef
              } memcachedClient.set(cKey, tagttl.get.ttl.toSeconds.toInt, definition)
              futureMaybeDefinition
          }
        })
    }

    tagttl match {
      case Some(t) =>
        val cacheKey = s"mosaic-definition-$projectId-${t.tag}"
        val futureMosaicDefinition = futureMosaicDefinitions.get(cacheKey, fetch)
        futureMosaicDefinitions.put(cacheKey, futureMosaicDefinition)
        futureMosaicDefinition
      case None =>
        ScenesToProjects.getMosaicDefinition(projectId)
    }
  }

  /** Fetch the tile for given resolution. If it is not present, use a tile from a lower zoom level */
  def fetch(id: RfLayerId, zoom: Int, col: Int, row: Int)(implicit database: Database):
      Future[Option[MultibandTile]] = {
    tileLayerMetadata(id, zoom).flatMap {
      case Some((sourceZoom, tlm)) =>
        val zdiff = zoom - sourceZoom
        val pdiff = 1<<zdiff
        val sourceKey = SpatialKey(col / pdiff, row / pdiff)
        if (tlm.bounds includes sourceKey)
          for ( maybeTile <- LayerCache.maybeTile(id, sourceZoom, sourceKey) ) yield {
            for (tile <- maybeTile) yield {
              val innerCol  = col % pdiff
              val innerRow  = row % pdiff
              val cols = tile.cols / pdiff
              val rows = tile.rows / pdiff
              tile.crop(GridBounds(
                colMin = innerCol * cols,
                rowMin = innerRow * rows,
                colMax = (innerCol + 1) * cols - 1,
                rowMax = (innerRow + 1) * rows - 1
              )).resample(256, 256)
            }
          }
        else
          Future.successful(None)
      case None =>
        Future.successful(None)
    }
  }

  def raw(
    projectId: UUID,
    zoom: Int, col: Int, row: Int
  )(implicit db: Database): Future[Option[MultibandTile]] = {

    // Lookup project definition
    // NOTE: raw does NOT cache the mosaicDefinition
    val mayhapMosaic: Future[Option[MosaicDefinition]] = mosaicDefinition(projectId, None)

    mayhapMosaic.flatMap {
      case None => // can't merge a project without mosaic definition
        Future.successful(Option.empty[MultibandTile])

      case Some(mosaic) =>
        val mayhapTiles =
          for (
            (sceneId, colorCorrectParams) <- mosaic.definition
          ) yield {
            val id = RfLayerId(sceneId)
            for {
              maybeTile <- Mosaic.fetch(id, zoom, col, row)
            } yield
              for (tile <- maybeTile) yield tile
          }

        Future.sequence(mayhapTiles).map { maybeTiles =>
          val tiles = maybeTiles.flatten
          if (tiles.nonEmpty)
            Option(tiles.reduce(_ merge _))
          else
            Option.empty[MultibandTile]
        }
    }
  }

  /** Mosaic tiles from TMS pyramids given that they are in the same projection.
    *   If a layer does not go up to requested zoom it will be up-sampled.
    *   Layers missing color correction in the mosaic definition will be excluded.
    *
    *   @param rgbOnly  This parameter determines whether or not the mosaic should return an RGB
    *                    MultibandTile or all available bands, regardless of their semantics.
    */
  def apply(
    projectId: UUID,
    zoom: Int, col: Int, row: Int,
    tag: Option[String] = None,
    rgbOnly: Boolean = true
  )(
    implicit db: Database
  ): Future[Option[MultibandTile]] = {

    // Lookup project definition
    val maybeMosaic: Future[Option[MosaicDefinition]] = tag match {
      case Some(t) =>
        // tag present, include in lookup to re-use cache
        mosaicDefinition(projectId, Option(TagWithTTL(tag=t, ttl=60.seconds)))
      case None =>
        // no tag to control cache rollover, so don't cache
        mosaicDefinition(projectId, None)
    }

    maybeMosaic.flatMap {
      case None => // can't merge a project without mosaic definition
        Future.successful(Option.empty[MultibandTile])

      case Some(mosaic) =>
        val maybeTiles =
          for ((sceneId, colorCorrectParams) <- mosaic.definition) yield {
            val id = RfLayerId(sceneId)
            if (rgbOnly) {
              colorCorrectParams match {
                case None =>
                  Future.successful(Option.empty[MultibandTile])
                case Some(params) =>
                  for {
                    maybeTile <- Mosaic.fetch(id, zoom, col, row)
                    hist <- LayerCache.bandHistogram(id, zoom)
                  } yield
                    for (tile <- maybeTile) yield params.colorCorrect(tile, hist)
              }
            } else { // Return all bands
              for {
                maybeTile <- Mosaic.fetch(id, zoom, col, row)
              } yield maybeTile
            }
          }

        Future.sequence(maybeTiles).map { maybeTiles =>
          val tiles = maybeTiles.flatten
          if (tiles.nonEmpty)
            Option(tiles.reduce(_ merge _))
          else
            Option.empty[MultibandTile]
      }
    }
  }
}
