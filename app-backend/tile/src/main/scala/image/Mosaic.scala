package com.azavea.rf.tile.image

import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.ScenesToProjects
import com.azavea.rf.datamodel.{ ColorCorrect, MosaicDefinition }

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import com.azavea.rf.tile._
import scala.concurrent.ExecutionContext.Implicits.global
import com.github.blemale.scaffeine.{Cache, AsyncLoadingCache, Scaffeine}
import scala.concurrent._
import scala.concurrent.duration._
import java.util.UUID

object Mosaic {
  /** Cache the result of metadata queries that may have required walking up the pyramid to find suitable layers */
  val cacheTileLayerMetadata: AsyncLoadingCache[(RfLayerId, Int), Option[(Int, TileLayerMetadata[SpatialKey])]] =
    Scaffeine()
      .expireAfterWrite(10.minutes)
      .maximumSize(1024)
      .buildAsyncFuture { case (id, zoom) =>
        val store = LayerCache.attributeStore(id.prefix)
        def readMetadata(tryZoom: Int): Option[(Int, TileLayerMetadata[SpatialKey])] =
          try {
            Some(tryZoom -> store.readMetadata[TileLayerMetadata[SpatialKey]](id.catalogId(tryZoom)))
          } catch {
            case e: AttributeNotFoundError if (tryZoom > 0) => readMetadata(tryZoom - 1)
          }
        Future { readMetadata(zoom) }
      }

  /** Cache the result of mosaic definition, use tag to control cache rollover */
  val cacheTaggedMosaicDefinition: AsyncLoadingCache[(Database, UUID, String), Option[MosaicDefinition]] =
    Scaffeine()
      .expireAfterWrite(5.minutes)
      .maximumSize(1024)
      .buildAsyncFuture { case (db, projectId, _) =>
        ScenesToProjects.getMosaicDefinition(projectId)(db)
      }

  /** Cache the result of mosaic definition, quick expiry just enough for a single map render */
  val cacheMosaicDefinition: AsyncLoadingCache[(Database, UUID), Option[MosaicDefinition]] =
    Scaffeine()
      .expireAfterWrite(5.seconds)
      .maximumSize(1024)
      .buildAsyncFuture { case (db, projectId) =>
        ScenesToProjects.getMosaicDefinition(projectId)(db)
      }


  /** Fetch the tile for given resolution. If it is not present, use a tile from a lower zoom level */
  def fetch(id: RfLayerId, zoom: Int, col: Int, row: Int): Future[Option[MultibandTile]] = {
    cacheTileLayerMetadata.get(id -> zoom).flatMap {
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
      case None => Future.successful(None)
    }
  }

  /** Mosaic tiles from TMS pyramids given that they are in the same projection.
   * If a layer does not go up to requested zoom it will be up-sampled.
   * Layers missing color correction in the mosaic definition will be excluded.
   */
  def apply(
    orgId: UUID,
    userId: String,
    projectId: UUID,
    zoom: Int, col: Int, row: Int,
    tag: Option[String] = None
  )(
    implicit db: Database
  ): Future[Option[MultibandTile]] = {

    // Lookup project definition
    val mayhapMosaic: Future[Option[MosaicDefinition]] = tag match {
      case Some(t) =>
        // tag present, include in lookup to re-use cache
        cacheTaggedMosaicDefinition.get((db, projectId, t))
      case None =>
        // no tag to control cache rollover, quick expiry
        cacheMosaicDefinition.get((db, projectId))
    }

    mayhapMosaic.flatMap {
      case None => // can't merge a project without mosaic definition
        Future.successful(Option.empty[MultibandTile])

      case Some(mosaic) =>
        val mayhapTiles =
          for ((sceneId, colorCorrectParams) <- mosaic.definition) yield {
            colorCorrectParams match {
              case None => // can't use a scene without color correction params
                Future.successful(Option.empty[MultibandTile])

              case Some(params) =>
                val id = RfLayerId(orgId, userId, sceneId)
                for {
                  maybeTile <- Mosaic.fetch(id, zoom, col, row)
                  hist <- LayerCache.bandHistogram(id, zoom)
                } yield
                  for (tile <- maybeTile) yield params.colorCorrect(tile, hist)
            }
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
}
