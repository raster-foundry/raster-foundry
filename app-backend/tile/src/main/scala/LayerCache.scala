package com.azavea.rf.tile

import com.azavea.rf.tile.image._
import com.azavea.rf.tile.tool.TileSources
import com.azavea.rf.datamodel.{Tool, ToolRun, User}
import com.azavea.rf.tool.eval._
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.params._
import com.azavea.rf.tool.ast.MapAlgebraAST
import com.azavea.rf.common._
import com.azavea.rf.common.ast._
import com.azavea.rf.common.cache._
import com.azavea.rf.common.cache.kryo.KryoMemcachedClient
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables._

import io.circe._
import io.circe.syntax._
import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.histogram._
import geotrellis.raster.io._
import geotrellis.vector.io._
import geotrellis.spark.io._
import geotrellis.spark._
import geotrellis.spark.tiling._
import geotrellis.proj4._
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
  private val astCache = HeapBackedMemcachedClient(memcachedClient)

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


  /** Calculate the histogram for the least resolute zoom level to automatically render tiles */
  def modelLayerGlobalHistogram(
    toolRunId: UUID,
    subNode: Option[UUID],
    user: User,
    voidCache: Boolean = false
  ): OptionT[Future, Histogram[Double]] = {
    val cacheKey = s"histogram-${toolRunId}-${subNode}-${user.id}"

    if (voidCache) histogramCache.delete(cacheKey)
    histogramCache.cachingOptionT(cacheKey) { implicit ec =>
      for {
        (tool, toolRun) <- LayerCache.toolAndToolRun(toolRunId, user, voidCache)
        (ast, params)   <- LayerCache.toolEvalRequirements(toolRunId, subNode, user, voidCache)
        (extent, zoom)  <- TileSources.fullDataWindow(params.sources)
        lztile          <- OptionT(Interpreter.interpretGlobal(ast, params.sources, params.overrides, extent, { r => TileSources.globalSource(extent, zoom, r) }).map(_.toOption))
        tile            <- OptionT.fromOption[Future](lztile.evaluateDouble)
      } yield {
        val hist = StreamingHistogram.fromTile(tile)
        val currentMetadata = params.metadata.getOrElse(ast.id, NodeMetadata())
        val updatedMetadata = currentMetadata.copy(histogram = Some(hist))
        val updatedParams = params.copy(metadata = params.metadata + (ast.id -> updatedMetadata))
        val updatedToolRun = toolRun.copy(executionParameters = updatedParams.asJson)
        try {
          database.db.run { ToolRuns.updateToolRun(updatedToolRun, toolRun.id, user) }
        } catch {
          case e: Exception =>
            logger.error(s"Unable to update ToolRun (${toolRun.id}): ${e.getMessage}")
        }

        hist
      }
    }
  }

  def toolAndToolRun(
    toolRunId: UUID,
    user: User,
    voidCache: Boolean = false
  ): OptionT[Future, (Tool.WithRelated, ToolRun)] =
    astCache.cachingOptionT(s"tool+run-$toolRunId-${user.id}") { implicit ec =>
      for {
        toolRun  <- OptionT(database.db.run(ToolRuns.getToolRun(toolRunId, user)))
        tool     <- OptionT(Tools.getTool(toolRun.tool, user))
      } yield (tool, toolRun)
    }



  /** Calculate all of the prerequisites to evaluation of an AST over a set of tile sources */
  def toolEvalRequirements(
    toolRunId: UUID,
    subNode: Option[UUID],
    user: User,
    voidCache: Boolean = false
  ): OptionT[Future, (MapAlgebraAST, EvalParams)] = {
    val cacheKey = s"ast+params-$toolRunId-${subNode}-${user.id}"
    if (voidCache) histogramCache.delete(cacheKey)
    astCache.cachingOptionT(cacheKey) { implicit ec =>
      for {
        (tool, toolRun) <- toolAndToolRun(toolRunId, user)
        oldAst      <- OptionT.fromOption[Future]({
                      logger.debug(s"Parsing Tool AST with ${tool.definition}")
                      val entireAST = tool.definition.as[MapAlgebraAST].valueOr(throw _)
                      subNode.flatMap(id => entireAST.find(id)).orElse(Some(entireAST))
                    })
        subs     <- assembleSubstitutions(oldAst, user)
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

  /** Calculate all of the prerequisites to evaluation of an AST over a set of tile sources */
  def toolRunColorMap(
    toolRunId: UUID,
    subNode: Option[UUID],
    user: User,
    voidCache: Boolean = false
  ): OptionT[Future, ColorMap] = {
    val cacheKey = s"colormap-$toolRunId-${subNode}-${user.id}"
    if (voidCache) astCache.delete(cacheKey)
    astCache.cachingOptionT(cacheKey) { implicit ec =>
      for {
        (tool, toolRun) <- LayerCache.toolAndToolRun(toolRunId, user, voidCache)
        (ast, params)   <- LayerCache.toolEvalRequirements(toolRunId, subNode, user, voidCache)
        nodeId          <- OptionT.pure[Future, UUID](subNode.getOrElse(ast.id))
        metadata        <- OptionT.fromOption[Future](params.metadata.get(nodeId))
        cRamp           <- OptionT.fromOption[Future](metadata.colorRamp)
                             .orElse(OptionT.pure[Future, ColorRamp](geotrellis.raster.render.ColorRamps.Viridis))
        cmap            <- OptionT.fromOption[Future](metadata.classMap.map(_.toColorMap)).orElse({
                               for {
                                 breaks <- OptionT.fromOption[Future](metadata.breaks)
                               } yield cRamp.toColorMap(breaks)
                             }).orElse({
                               for {
                                 hist <- OptionT.fromOption[Future](metadata.histogram)
                                   .orElse(LayerCache.modelLayerGlobalHistogram(toolRunId, subNode, user, voidCache))
                               } yield cRamp.toColorMap(hist)
                             })
      } yield cmap
    }
  }
}
