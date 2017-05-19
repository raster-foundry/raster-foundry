package com.azavea.rf.tile.tool

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global

import cats.implicits._
import com.azavea.rf.database.Database
import com.azavea.rf.tile._
import com.azavea.rf.tile.image._
import com.azavea.rf.tool.ast._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.slick.Projected
import geotrellis.spark._
import geotrellis.spark.tiling._


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
  def cachedGlobalSource(r: RFMLRaster)(implicit database: Database): Future[Option[Tile]] =
    r match {
      case scene @ SceneRaster(sceneId, Some(band)) =>
        LayerCache.layerTileForExtent(sceneId, 1, WebMercator.worldExtent)
          .map(tile => tile.bands(band)).value

      case scene @ SceneRaster(sceneId, None) =>
        logger.warn(s"Request for $scene does not contain band index")
        Future.successful(None)

      case project @ ProjectRaster(projId, Some(band)) =>
        Mosaic.fetchRenderedExtent(projId, 1, Some(Projected(WebMercator.worldExtent.toPolygon, 4326)))
          .map({ tile => tile.bands(band) }).value

      case project @ ProjectRaster(projId, None) =>
        logger.warn(s"Request for $project does not contain band index")
        Future.successful(None)
    }

  /** This source provides support for z/x/y TMS tiles */
  def cachedTmsSource(r: RFMLRaster, z: Int, x: Int, y: Int)(implicit database: Database): Future[Option[Tile]] =
    r match {
      case scene @ SceneRaster(sceneId, Some(band)) =>
        LayerCache.layerTile(sceneId, z, SpatialKey(x, y))
          .map(tile => tile.bands(band)).value

      case scene @ SceneRaster(sceneId, None) =>
        logger.warn(s"Request for $scene does not contain band index")
        Future.successful(None)

      case project @ ProjectRaster(projId, Some(band)) =>
        Mosaic.fetch(projId, z, x, y)
          .map(tile => tile.bands(band)).value

      case project @ ProjectRaster(projId, None) =>
        logger.warn(s"Request for $project does not contain band index")
        Future.successful(None)

      case _ =>
        Future.failed(new Exception(s"Cannot handle $r"))
    }
}
