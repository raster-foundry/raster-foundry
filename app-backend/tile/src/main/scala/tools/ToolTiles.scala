package com.azavea.rf.tile.tools

import java.util.UUID

import com.azavea.rf.common.cache._
import com.azavea.rf.common.cache.kryo.KryoMemcachedClient
import com.azavea.rf.common.utils.TileUtils
import com.azavea.rf.common.KamonTraceRF
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables._
import com.azavea.rf.datamodel._
import com.azavea.rf.tile.project._
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.eval._
import com.azavea.rf.tool.params._

import akka.dispatch.MessageDispatcher
import cats.data._
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import io.circe.syntax._
import geotrellis.raster._
import geotrellis.raster.histogram._
import geotrellis.raster.render._
import geotrellis.slick.Projected
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.postgres.PostgresAttributeStore
import geotrellis.spark.io.s3._
import geotrellis.vector.Extent

import scala.concurrent._
import scala.util._

trait ToolTiles extends LazyLogging with KamonTraceRF with ProjectTileUtils with TileIO {
  implicit val database: Database
  implicit val blockingDispatcher: MessageDispatcher
  val memcachedClient: KryoMemcachedClient
  val rfCache: CacheClient
  val store: PostgresAttributeStore


  def flattenTiles(tiles: Future[Seq[MultibandTile]]): OptionT[Future, MultibandTile] = {
    val mergedTiles = tiles.map { tiles =>
      if (tiles.nonEmpty) {
        Option(tiles.reduce(_ merge _))
      } else {
        Option.empty[MultibandTile]
      }
    }
    OptionT(mergedTiles)
  }


  /** Fetch all bands of a [[MultibandTile]] and return them without assuming anything of their semantics
    * to then be passed on for operations
    *
    * @param projectId project to request tile for
    * @param zoom level for tile
    * @param col x coordinate for tile
    * @param row y coordinate for tile
    * @return [[MultibandTile]]
    */
  def getToolTileZXY(projectId: UUID, zoom: Int, col: Int, row: Int): OptionT[Future, MultibandTile] = {
    val polygonBbox = TileUtils.getTileBounds(zoom, col, row)
    val mosaicDefinition = getMosaicDefinition(projectId, Option(polygonBbox))

    mosaicDefinition.flatMap { mosaicSequence =>
      val tiles = mosaicSequence.map { case MosaicDefinition(sceneId, _) =>
        val tileOption = for {
          tile <- getSceneTileZXY(sceneId, zoom, col, row)
        } yield {
          tile
        }
        tileOption.value
      }
      flattenTiles(Future.sequence(tiles).map(_.flatten))
    }
  }

  /** Calculate the histogram for the least resolute zoom level to automatically render tiles
    *
    * @param toolRunId tool run to request global histogram for
    * @param subNode node to request histogram for
    * @param user user requesting node
    * @param voidCache boolean whether to void cache
    * @return
    */
  def modelLayerGlobalHistogram(
    toolRunId: UUID,
    subNode: Option[UUID],
    user: User,
    voidCache: Boolean = false
  ): OptionT[Future, Histogram[Double]] = {
    val cacheKey = s"histogram-$toolRunId-$subNode-${user.id}"

    if (voidCache) rfCache.delete(cacheKey)
    rfCache.cachingOptionT(cacheKey, doCache = cacheConfig.tool.enabled) {
      for {
        (tool, toolRun) <- toolAndToolRun(toolRunId, user, voidCache)
        (ast, params) <- toolEvalRequirements(toolRunId, subNode, user, voidCache)
        (extent, zoom) <- fullDataWindow(params.sources)
        lztile <- OptionT(Interpreter.interpretGlobal(ast, params.sources, params.overrides,
          extent, { r => globalSource(extent, zoom, r) }).map(_.toOption)
        )
        tile <- OptionT.fromOption[Future](lztile.evaluateDouble)
      } yield {
        val hist = StreamingHistogram.fromTile(tile)
        val currentMetadata = params.metadata.getOrElse(ast.id, NodeMetadata())
        val updatedMetadata = currentMetadata.copy(histogram = Some(hist))
        val updatedParams = params.copy(metadata = params.metadata + (ast.id -> updatedMetadata))
        val updatedToolRun = toolRun.copy(executionParameters = updatedParams.asJson)
        try {
          database.db.run {
            ToolRuns.updateToolRun(updatedToolRun, toolRun.id, user)
          }
        } catch {
          case e: Exception =>
            logger.error(s"Unable to update ToolRun (${toolRun.id}): ${e.getMessage}")
        }
        hist
      }
    }
  }

  /** Get tool and tool run given a tool run ID
    *
    * @param toolRunId ID of tool run
    * @param user user requesting data
    * @param voidCache whether to void the cache
    * @return
    */
  def toolAndToolRun(toolRunId: UUID, user: User, voidCache: Boolean = false): OptionT[Future, (Tool.WithRelated, ToolRun)] = {
    rfCache.cachingOptionT(s"tool+run-$toolRunId-${user.id}", doCache = cacheConfig.tool.enabled) {
      for {
        toolRun <- OptionT(database.db.run(ToolRuns.getToolRun(toolRunId, user)))
        tool <- OptionT(Tools.getTool(toolRun.tool, user))
      } yield (tool, toolRun)
    }
  }

  /** Calculate all of the prerequisites to evaluation of an AST over a set of tile sources
    *
    * @param toolRunId ID of tool run to evaluate
    * @param subNode node to evaluate for
    * @param user user requesting tool results
    * @param voidCache whether or not to void the cache
    * @return
    */
  def toolEvalRequirements(
    toolRunId: UUID,
    subNode: Option[UUID],
    user: User,
    voidCache: Boolean = false
  ): OptionT[Future, (MapAlgebraAST, EvalParams)] = {
    val cacheKey = s"ast-params-$toolRunId-$subNode-${user.id}"
    if (voidCache) rfCache.delete(cacheKey)
    rfCache.cachingOptionT(cacheKey, doCache = cacheConfig.tool.enabled) {
      for {
        (tool, toolRun) <- toolAndToolRun(toolRunId, user)
        oldAst   <- OptionT.fromOption[Future]({
          logger.debug(s"Parsing Tool AST with ${tool.definition}")
          val entireAST = tool.definition.as[MapAlgebraAST].valueOr(throw _)
          subNode.flatMap(id => entireAST.find(id)).orElse(Some(entireAST))
        })
        subs     <- assembleSubstitutions(oldAst, { id: UUID =>
          OptionT(Tools.getTool(id, user))
            .map({ referrent => referrent.definition.as[MapAlgebraAST].valueOr(throw _) })
            .value
        })
        ast      <- OptionT.fromOption[Future](oldAst.substitute(subs))
        nodeId   <- OptionT.pure[Future, UUID](subNode.getOrElse(ast.id))
        params   <- OptionT.pure[Future, EvalParams]({
          logger.debug(s"Parsing ToolRun parameters with ${toolRun.executionParameters}")
          val parsedParams = toolRun.executionParameters.as[EvalParams].valueOr(throw _)
          val defaults = ast.metadata
          val overrides = parsedParams.metadata.get(ast.id)
          val md = (overrides |@| defaults).map(_.fallbackTo(_))
            .orElse(overrides)
            .orElse(defaults)
            .getOrElse(NodeMetadata())

          EvalParams(
            parsedParams.sources,
            parsedParams.metadata + (ast.id -> md)
          )
        })
      } yield (ast, params)
    }
  }

  /** Calculate all of the prerequisites to evaluation of an AST over a set of tile sources
    *
    * @param toolRunId ID of tool run to create color map for
    * @param subNode node to request color ramp for
    * @param user user requesting tool color ramp
    * @param voidCache whether or not to void cache
    * @return
    */
  def toolRunColorMap(
    toolRunId: UUID,
    subNode: Option[UUID],
    user: User,
    voidCache: Boolean = false
  ): OptionT[Future, ColorMap] = traceName("toolRunColorMap") {
    val cacheKey = s"colormap-$toolRunId-$subNode-${user.id}"
    if (voidCache) rfCache.delete(cacheKey)
    rfCache.cachingOptionT(cacheKey, doCache = cacheConfig.tool.enabled) {
      for {
        (tool, toolRun) <- toolAndToolRun(toolRunId, user, voidCache)
        (ast, params) <- toolEvalRequirements(toolRunId, subNode, user, voidCache)
        nodeId <- OptionT.pure[Future, UUID](subNode.getOrElse(ast.id))
        metadata <- OptionT.fromOption[Future](params.metadata.get(nodeId))
        cRamp <- OptionT.fromOption[Future](metadata.colorRamp).orElse(OptionT.pure[Future, ColorRamp](geotrellis.raster.render.ColorRamps.Viridis))
        cmap <- OptionT.fromOption[Future](metadata.classMap.map(_.toColorMap)).orElse({
          for {
            breaks <- OptionT.fromOption[Future](metadata.breaks)
          } yield cRamp.toColorMap(breaks)
        }).orElse({
          for {
            hist <- OptionT.fromOption[Future](metadata.histogram).orElse(modelLayerGlobalHistogram(toolRunId, subNode, user, voidCache))
          } yield cRamp.toColorMap(hist)
        })
      } yield cmap
    }
  }

  /** Find extent and zoom for a given set of rasters
    *
    * @param rasters to find extent for
    * @return extent and zoom level
    */
  def fullDataWindow(rasters: Map[UUID, RFMLRaster]): OptionT[Future, (Extent, Int)] = {
    rasters
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
  def dataWindow(raster: RFMLRaster): OptionT[Future, (Extent, Int)] = raster match {
    case SceneRaster(id, Some(_), _) => OptionT.fromOption(findMinAcceptableSceneZoom(id, 256))  // TODO: 512?
    case ProjectRaster(id, Some(_), _) => findMinAcceptableProjectZoom(256, getMosaicDefinition(id)) // TODO: 512?
    /* Don't attempt work for a RFMLRaster which will fail AST validation anyway */
    case _ => OptionT.none
  }

  /** This source will return the raster for all of zoom level 1 and is
    *  useful for generating a histogram which allows binning values into
    *  quantiles.
    */
  def globalSource(extent: Extent, zoom: Int, raster: RFMLRaster): Future[Option[TileWithNeighbors]] = raster match {
    case SceneRaster(id, Some(band), maybeND) =>
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
          case Success(tile) => Some(TileWithNeighbors(tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)), None))
          case Failure(e) =>
            logger.error(s"Query layer $id at zoom $zoom for $extent: ${e.getMessage}")
            None
        }
      }

    case ProjectRaster(projId, Some(band), maybeND) =>
      val mosaicDefinition = getMosaicDefinition(projId)
      getRawProjectMosaicForExtent(projId, zoom, Some(Projected(extent.toPolygon, 3857)), mosaicDefinition)
        .map({ tile =>
          TileWithNeighbors(tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)), None)
        }).value

    case _ => Future.successful(None)
  }

  /** Get a tile with surrounding neighbors
    *
    * @param raster raster to get neighboring tiles
    * @param hasBuffer whether buffer is required
    * @param z zoom level
    * @param x column
    * @param y row
    * @return
    */
  def getTileWithNeighbors(raster: RFMLRaster, hasBuffer: Boolean, z: Int, x: Int, y: Int): Future[Option[TileWithNeighbors]] = {
    lazy val ndtile = IntConstantTile(NODATA, 256, 256)
    raster match {
      case scene @ SceneRaster(sceneId, Some(band), maybeND) =>
        implicit val sceneIds = Set(sceneId)
        if (hasBuffer)
          (for {
            tl <- getSceneTile(sceneId, z, SpatialKey(x - 1, y - 1))
              .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
              .orElse(OptionT.pure[Future, Tile](ndtile))
            tm <- getSceneTile(sceneId, z, SpatialKey(x, y - 1))
              .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
              .orElse(OptionT.pure[Future, Tile](ndtile))
            tr <- getSceneTile(sceneId, z, SpatialKey(x + 1 , y - 1))
              .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
              .orElse(OptionT.pure[Future, Tile](ndtile))
            ml <- getSceneTile(sceneId, z, SpatialKey(x - 1, y))
              .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
              .orElse(OptionT.pure[Future, Tile](ndtile))
            mm <- getSceneTile(sceneId, z, SpatialKey(x, y))
              .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
            mr <- getSceneTile(sceneId, z, SpatialKey(x + 1, y))
              .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
              .orElse(OptionT.pure[Future, Tile](ndtile))
            bl <- getSceneTile(sceneId, z, SpatialKey(x - 1, y + 1))
              .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
              .orElse(OptionT.pure[Future, Tile](ndtile))
            bm <- getSceneTile(sceneId, z, SpatialKey(x, y + 1))
              .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
              .orElse(OptionT.pure[Future, Tile](ndtile))
            br <- getSceneTile(sceneId, z, SpatialKey(x + 1, y + 1))
              .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
              .orElse(OptionT.pure[Future, Tile](ndtile))
          } yield {
            TileWithNeighbors(mm, Some(NeighboringTiles(tl, tm, tr, ml, mr,bl, bm, br)))
          }).value
        else
          getSceneTile(sceneId, z, SpatialKey(x, y))
            .map({ tile => TileWithNeighbors(tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)), None) })
            .value

      case scene @ SceneRaster(sceneId, None, _) =>
        implicit val sceneIds = Set(sceneId)
        logger.warn(s"Request for $scene does not contain band index")
        Future.successful(None)

      case project @ ProjectRaster(projId, Some(band), maybeND) =>
        if (hasBuffer)
          (for {
            tl <- getToolTileZXY(projId, z, x - 1, y - 1)
              .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
              .orElse(OptionT.pure[Future, Tile](ndtile))
            tm <- getToolTileZXY(projId, z, x, y - 1)
              .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
              .orElse(OptionT.pure[Future, Tile](ndtile))
            tr <- getToolTileZXY(projId, z, x, y - 1)
              .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
              .orElse(OptionT.pure[Future, Tile](ndtile))
            ml <- getToolTileZXY(projId, z, x - 1, y)
              .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
              .orElse(OptionT.pure[Future, Tile](ndtile))
            mm <- getToolTileZXY(projId, z, x, y)
              .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
            mr <- getToolTileZXY(projId, z, x + 1, y)
              .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
              .orElse(OptionT.pure[Future, Tile](ndtile))
            bl <- getToolTileZXY(projId, z, x - 1, y + 1)
              .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
              .orElse(OptionT.pure[Future, Tile](ndtile))
            bm <- getToolTileZXY(projId, z, x, y + 1)
              .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
              .orElse(OptionT.pure[Future, Tile](ndtile))
            br <- getToolTileZXY(projId, z, x + 1, y + 1)
              .map({ tile => tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)) })
              .orElse(OptionT.pure[Future, Tile](ndtile))
          } yield {
            TileWithNeighbors(mm, Some(NeighboringTiles(tl, tm, tr, ml, mr,bl, bm, br)))
          }).value
        else
          getToolTileZXY(projId, z, x, y)
            .map({ tile => TileWithNeighbors(tile.band(band).interpretAs(maybeND.getOrElse(tile.cellType)), None) })
            .value

      case project @ ProjectRaster(projId, None, _) =>
        logger.warn(s"Request for $project does not contain band index")
        Future.successful(None)

      case _ =>
        Future.failed(new Exception(s"Cannot handle $raster"))
    }
  }

}

