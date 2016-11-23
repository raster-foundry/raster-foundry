package com.azavea.rf.tile.image

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import com.azavea.rf.tile._
import scala.concurrent.ExecutionContext.Implicits.global
import com.github.blemale.scaffeine.{ AsyncLoadingCache, Scaffeine }
import scala.concurrent._
import scala.concurrent.duration._

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
    * Missing layers will be excluded from the mosaic.
    */
  def apply(
    params: ColorCorrect.Params,
    ids: Traversable[RfLayerId],
    zoom: Int, col: Int, row: Int
  ): Future[Option[MultibandTile]] = {
    val futureMaybeTiles =
      for (id <- ids) yield {
        for {
          maybeTile <- Mosaic.fetch(id, zoom, col, row)
          hist <- LayerCache.bandHistogram(id, zoom)
        } yield {
          maybeTile.map { tile =>
            val (rgbTile, rgbHist) = params.reorderBands(tile, hist)
            ColorCorrect(rgbTile, rgbHist, params)
          }
        }
      }

    Future.sequence(futureMaybeTiles).map { maybeTiles =>
      val tiles = maybeTiles.flatten
      if (tiles.nonEmpty)
        Some(tiles.reduce(_ merge _))
      else
        None
    }
  }
}
