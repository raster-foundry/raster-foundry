package com.azavea.rf.tile.tool

import com.azavea.rf.database.Database
import com.azavea.rf.tile._
import com.azavea.rf.tile.image._
import com.azavea.rf.tool.ast._

import com.typesafe.scalalogging.LazyLogging
import cats.implicits._
import geotrellis.raster._
import geotrellis.slick.Projected
import geotrellis.spark._
import geotrellis.spark.io.s3._
import geotrellis.spark.io._

import scala.util._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global


/** Interpreting a [[MapAlgebraAST]] requires providing a function from
  *  (at least) an RFMLRaster (the source/terminal-node type of the AST)
  *  to a Future[Option[Tile]]. This object provides instance of such
  *  functions.
  */
object TileSources extends LazyLogging {

  /** This source will return the raster for all of zoom level 1 and is
    *  useful for generating a histogram which allows binning values into
    *  quantiles.
    */
  def globalSource(r: RFMLRaster)(implicit database: Database): Future[Option[Tile]] =
    r match {
      case scene @ SceneRaster(sceneId, Some(band)) =>
        LayerCache.attributeStoreForLayer(sceneId).mapFilter { case (store, _) =>
          GlobalSummary.minAcceptableSceneZoom(sceneId, store).flatMap { case (extent, zoom) =>
            blocking {
              Try {
                val layerId = LayerId(sceneId.toString, zoom)
                S3CollectionLayerReader(store)
                  .query[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](layerId)
                  .result
                  .stitch
                  .crop(extent)
                  .tile
              } match {
                case Success(tile) => Option(tile)
                case Failure(e) =>
                  logger.error(s"Query layer $sceneId at zoom $zoom for $extent: ${e.getMessage}")
                  None
              }
            }
          }
        }.map({ mbtile => mbtile.band(band) }).value

      case scene @ SceneRaster(sceneId, None) =>
        logger.warn(s"Request for $scene does not contain band index")
        Future.successful(None)

      case project @ ProjectRaster(projId, Some(band)) =>
        GlobalSummary.minAcceptableProjectZoom(projId).flatMap { case (extent, zoom) =>
          Mosaic.rawForExtent(projId, zoom, Some(Projected(extent.toPolygon, 3857)))
            .map({ tile => tile.band(band) })
        }.value

      case project @ ProjectRaster(projId, None) =>
        logger.warn(s"Request for $project does not contain band index")
        Future.successful(None)

    }

  /** This source provides support for z/x/y TMS tiles */
  def cachedTmsSource(r: RFMLRaster, z: Int, x: Int, y: Int)(implicit database: Database): Future[Option[Tile]] =
    r match {
      case scene @ SceneRaster(sceneId, Some(band)) =>
        LayerCache.layerTile(sceneId, z, SpatialKey(x, y))
          .map(tile => tile.band(band)).value

      case scene @ SceneRaster(sceneId, None) =>
        logger.warn(s"Request for $scene does not contain band index")
        Future.successful(None)

      case project @ ProjectRaster(projId, Some(band)) =>
        Mosaic.raw(projId, z, x, y)
          .map(tile => tile.band(band)).value

      case project @ ProjectRaster(projId, None) =>
        logger.warn(s"Request for $project does not contain band index")
        Future.successful(None)

      case _ =>
        Future.failed(new Exception(s"Cannot handle $r"))
    }
}
