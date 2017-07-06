package com.azavea.rf.tile.tool

import com.azavea.rf.database.Database
import com.azavea.rf.tile._
import com.azavea.rf.tile.image._
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.params._

import com.typesafe.scalalogging.LazyLogging
import cats.data._
import cats.implicits._
import geotrellis.raster._
import geotrellis.slick.Projected
import geotrellis.spark._
import geotrellis.spark.io.s3._
import geotrellis.spark.io._
import geotrellis.vector.Extent

import java.util.UUID
import scala.util._
import scala.concurrent._

/** Interpreting a [[MapAlgebraAST]] requires providing a function from
  *  (at least) an RFMLRaster (the source/terminal-node type of the AST)
  *  to a Future[Option[Tile]]. This object provides instance of such
  *  functions.
  */
object TileSources extends LazyLogging {

  /** Given the data sources for some AST, determine the "data window" for
    * the entire data set. This ensures that global AST interpretation will behave
    * correctly, so that valid  histograms can be generated.
    */
  def fullDataWindow(
    rs: Map[UUID, RFMLRaster]
  )(implicit database: Database, ec: ExecutionContext): OptionT[Future, (Extent, Int)] = {
    rs
      .values
      .toStream
      .map(dataWindow)
      .sequence
      .map({ pairs =>
        val (extents, zooms) = pairs.unzip
        val extent: Extent = extents.reduce(_ combine _)

        /* The average of all the reported optimal zoom levels. */
        val zoom: Int = zooms.sum / zooms.length

        (extent, zoom)
      })
  }

  /** Given a reference to a source of data, computes the "data window" of
    * the entire dataset. The `Int` is the minimally acceptable zoom level at
    * which one could read a Layer for the purpose of calculating a representative
    * histogram.
    */
  def dataWindow(r: RFMLRaster)(implicit database: Database, ec: ExecutionContext): OptionT[Future, (Extent, Int)] = r match {
    case SceneRaster(id, Some(_), _) => {
      LayerCache.attributeStoreForLayer(id).mapFilter({ case (store, _) =>
        GlobalSummary.minAcceptableSceneZoom(id, store, 256)  // TODO: 512?
      })
    }
    case ProjectRaster(id, Some(_), _) => GlobalSummary.minAcceptableProjectZoom(id, 256) // TODO: 512?

    /* Don't attempt work for a RFMLRaster which will fail AST validation anyway */
    case _ => OptionT.none
  }

  /** This source will return the raster for all of zoom level 1 and is
    *  useful for generating a histogram which allows binning values into
    *  quantiles.
    */
  def globalSource(
    extent: Extent,
    zoom: Int,
    r: RFMLRaster
  )(implicit database: Database, ec: ExecutionContext): Future[Option[Tile]] = r match {
    case SceneRaster(id, Some(band), maybeND) =>
      LayerCache.attributeStoreForLayer(id).mapFilter({ case (store, _) =>
        blocking {
          Try {
            val layerId = LayerId(id.toString, zoom)

            S3CollectionLayerReader(store)
              .query[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](layerId)
              .result
              .stitch
              .crop(extent)
              .tile
          } match {
            case Success(tile) => Option(tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)))
            case Failure(e) =>
              logger.error(s"Query layer $id at zoom $zoom for $extent: ${e.getMessage}")
              None
          }
        }
      }).value

    case ProjectRaster(projId, Some(band), maybeND) => {
      Mosaic.rawForExtent(projId, zoom, Some(Projected(extent.toPolygon, 3857)))
        .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) }).value
    }

    case _ => Future.successful(None)
  }

  /** This source provides support for z/x/y TMS tiles */
  def cachedTmsSource(r: RFMLRaster, z: Int, x: Int, y: Int)(implicit database: Database, ec: ExecutionContext): Future[Option[Tile]] =
    r match {
      case scene @ SceneRaster(sceneId, Some(band), maybeND) =>
        LayerCache.layerTile(sceneId, z, SpatialKey(x, y))
          .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) }).value

      case scene @ SceneRaster(sceneId, None, _) =>
        logger.warn(s"Request for $scene does not contain band index")
        Future.successful(None)

      case project @ ProjectRaster(projId, Some(band), maybeND) =>
        Mosaic.raw(projId, z, x, y)
          .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) }).value

      case project @ ProjectRaster(projId, None, _) =>
        logger.warn(s"Request for $project does not contain band index")
        Future.successful(None)

      case _ =>
        Future.failed(new Exception(s"Cannot handle $r"))
    }
}
