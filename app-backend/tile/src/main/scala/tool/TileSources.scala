package com.azavea.rf.tile.tool

import com.azavea.rf.database.Database
import com.azavea.rf.tile._
import com.azavea.rf.tile.image._
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.eval._
import com.typesafe.scalalogging.LazyLogging
import cats.data.{NonEmptyList => NEL, _}
import cats.data.Validated._
import cats.implicits._
import geotrellis.raster._
import geotrellis.slick.Projected
import geotrellis.spark._
import geotrellis.spark.io.s3._
import geotrellis.spark.io._
import geotrellis.vector.Extent
import geotrellis.spark.io.postgres.PostgresAttributeStore

import scala.util._
import scala.concurrent._
import java.util.UUID


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

  implicit val database = Database.DEFAULT
  val system = AkkaSystem.system
  implicit val blockingDispatcher = system.dispatchers.lookup("blocking-dispatcher")
  val store = PostgresAttributeStore()

  def fullDataWindow(
    rs: Set[RFMLRaster]
  )(implicit database: Database): OptionT[Future, (Extent, Int)] = {
    rs
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
  def dataWindow(r: RFMLRaster)(implicit database: Database): OptionT[Future, (Extent, Int)] = r match {
    case MapAlgebraAST.SceneRaster(id, sceneId, Some(_), _, _) => {
      implicit val sceneIds = Set(id)
      OptionT.fromOption(GlobalSummary.minAcceptableSceneZoom(sceneId, store, 256))
    }
    case MapAlgebraAST.ProjectRaster(id, projId, Some(_), _, _) => {
      implicit val sceneIds = Set(id)
      GlobalSummary.minAcceptableProjectZoom(projId, 256)
    }

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
  )(implicit database: Database): Future[Interpreted[TileWithNeighbors]] = r match {
    case sr@MapAlgebraAST.SceneRaster(id, sceneId, Some(band), maybeND, _) =>
      implicit val sceneIds = Set(id)
      Future {
          Try {
            val layerId = LayerId(id.toString, zoom)

            S3CollectionLayerReader(store)
              .query[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](layerId)
              .result
              .stitch
              .crop(extent)
              .tile
          } match {
            case Success(tile) =>
              Valid(TileWithNeighbors(tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)), None))
            case Failure(e) =>
              Invalid(NEL.of(RasterRetrievalError(sr)))
          }
      }
    case pr@MapAlgebraAST.ProjectRaster(id, projId, Some(band), maybeND, _) =>
      Mosaic.rawForExtent(projId, zoom, Some(Projected(extent.toPolygon, 3857))).value.map({ maybeTile =>
        maybeTile match {
          case Some(tile) =>
            Valid(TileWithNeighbors(tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)), None))
          case None =>
            Invalid(NEL.of(RasterRetrievalError(pr)))
        }
      })
    case r: MapAlgebraAST =>
      Future.successful(Invalid(NEL.of(RasterRetrievalError(r))))
  }

  /** This source provides support for z/x/y TMS tiles */
  def cachedTmsSource(r: RFMLRaster, hasBuffer: Boolean, z: Int, x: Int, y: Int)(implicit database: Database): Future[Interpreted[TileWithNeighbors]] = {
    lazy val ndtile = IntConstantTile(NODATA, 256, 256)
    r match {
      case scene @ MapAlgebraAST.SceneRaster(id, sceneId, Some(band), maybeND, _) =>
        implicit val sceneIds = Set(sceneId)
        val futureSource: OptionT[Future, TileWithNeighbors] = if (hasBuffer)
          (for {
            tl <- LayerCache.layerTile(sceneId, z, SpatialKey(x - 1, y - 1))
                    .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
                    .orElse(OptionT.pure[Future, Tile](ndtile))
            tm <- LayerCache.layerTile(sceneId, z, SpatialKey(x, y - 1))
                    .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
                    .orElse(OptionT.pure[Future, Tile](ndtile))
            tr <- LayerCache.layerTile(sceneId, z, SpatialKey(x + 1 , y - 1))
                    .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
                    .orElse(OptionT.pure[Future, Tile](ndtile))
            ml <- LayerCache.layerTile(sceneId, z, SpatialKey(x - 1, y))
                    .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
                    .orElse(OptionT.pure[Future, Tile](ndtile))
            mm <- LayerCache.layerTile(sceneId, z, SpatialKey(x, y))
                    .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
            mr <- LayerCache.layerTile(sceneId, z, SpatialKey(x + 1, y))
                    .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
                    .orElse(OptionT.pure[Future, Tile](ndtile))
            bl <- LayerCache.layerTile(sceneId, z, SpatialKey(x - 1, y + 1))
                    .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
                    .orElse(OptionT.pure[Future, Tile](ndtile))
            bm <- LayerCache.layerTile(sceneId, z, SpatialKey(x, y + 1))
                    .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
                    .orElse(OptionT.pure[Future, Tile](ndtile))
            br <- LayerCache.layerTile(sceneId, z, SpatialKey(x + 1, y + 1))
                    .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
                    .orElse(OptionT.pure[Future, Tile](ndtile))
          } yield {
            TileWithNeighbors(mm, Some(NeighboringTiles(tl, tm, tr, ml, mr,bl, bm, br)))
          })
        else
          LayerCache.layerTile(sceneId, z, SpatialKey(x, y)).map({ tile =>
            TileWithNeighbors(tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)), None)
          })
        futureSource.value.map({ maybeTile =>
          maybeTile match {
            case Some(tile) => Valid(tile)
            case None => Valid(TileWithNeighbors(ndtile, None))
          }
        })


      case scene @ MapAlgebraAST.SceneRaster(id, sceneId, None, _, _) =>
        implicit val sceneIds = Set(sceneId)
        logger.warn(s"Request for $scene does not contain band index")
        Future.successful(Invalid(NEL.of(NoBandGiven(id))))

      case project @ MapAlgebraAST.ProjectRaster(id, projId, Some(band), maybeND, _) =>
        val futureSource = if (hasBuffer)
          (for {
            tl <- Mosaic.raw(projId, z, x - 1, y - 1)
                    .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
                    .orElse(OptionT.pure[Future, Tile](ndtile))
            tm <- Mosaic.raw(projId, z, x, y - 1)
                    .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
                    .orElse(OptionT.pure[Future, Tile](ndtile))
            tr <- Mosaic.raw(projId, z, x, y - 1)
                    .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
                    .orElse(OptionT.pure[Future, Tile](ndtile))
            ml <- Mosaic.raw(projId, z, x - 1, y)
                    .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
                    .orElse(OptionT.pure[Future, Tile](ndtile))
            mm <- Mosaic.raw(projId, z, x, y)
                    .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
            mr <- Mosaic.raw(projId, z, x + 1, y)
                    .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
                    .orElse(OptionT.pure[Future, Tile](ndtile))
            bl <- Mosaic.raw(projId, z, x - 1, y + 1)
                    .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
                    .orElse(OptionT.pure[Future, Tile](ndtile))
            bm <- Mosaic.raw(projId, z, x, y + 1)
                    .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
                    .orElse(OptionT.pure[Future, Tile](ndtile))
            br <- Mosaic.raw(projId, z, x + 1, y + 1)
                    .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
                    .orElse(OptionT.pure[Future, Tile](ndtile))
          } yield {
            TileWithNeighbors(mm, Some(NeighboringTiles(tl, tm, tr, ml, mr,bl, bm, br)))
          })
        else
          Mosaic.raw(projId, z, x, y)
            .map({ tile => TileWithNeighbors(tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)), None) })

        futureSource.value.map({ maybeSource =>
          maybeSource match {
            case Some(t) => Valid(t)
            case None => Valid(TileWithNeighbors(ndtile, None))
          }
        })


      case project @ MapAlgebraAST.ProjectRaster(id, projId, None, _, _) =>
        logger.warn(s"Request for $project does not contain band index")
        Future.successful(Invalid(NEL.of(NoBandGiven(id))))

      case r: MapAlgebraAST =>
        Future.successful(Invalid(NEL.of(RasterRetrievalError(r))))
    }
  }
}
