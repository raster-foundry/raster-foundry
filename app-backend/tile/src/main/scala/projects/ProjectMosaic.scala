package com.azavea.rf.tile.project

import java.util.UUID

import com.azavea.rf.common.cache._
import com.azavea.rf.common.cache.kryo.KryoMemcachedClient
import com.azavea.rf.common.utils.TileUtils
import com.azavea.rf.common.{KamonTraceRF, Config => CommonConfig}
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.ScenesToProjects
import com.azavea.rf.datamodel._

import akka.dispatch.MessageDispatcher
import cats.data._
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.histogram._
import geotrellis.slick.Projected
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.postgres.PostgresAttributeStore
import geotrellis.spark.io.s3._
import geotrellis.vector._
import geotrellis.vector.io._

import scala.concurrent._


trait ProjectMosaic extends LazyLogging with KamonTraceRF with ProjectTileUtils with TileIO {
  implicit val database: Database
  implicit val blockingDispatcher: MessageDispatcher
  val memcachedClient: KryoMemcachedClient
  val rfCache: CacheClient
  val store: PostgresAttributeStore

  override val cacheConfig = CommonConfig.memcached
  /** Get the [[RasterExtent]] which describes the meaningful subset of a layer from metadata */


  /**   Render a png from TMS pyramids given that they are in the same projection.
    *   If a layer does not go up to requested zoom it will be up-sampled.
    *   Layers missing color correction in the mosaic definition will be excluded.
    *   The size of the image will depend on the selected zoom and bbox.
    *
    *   Note:
    *   Currently, if the render takes too long, it will time out. Given enough requests, this
    *   could cause us to essentially ddos ourselves, so we probably want to change
    *   this from a simple endpoint to an airflow operation: IE the request kicks off
    *   a render job then returns the job id
    *
    *   @param zoomOption  the zoom level to use
    *   @param bboxOption the bounding box for the image
    *   @param colorCorrect setting to determine if color correction should be applied
    */
  def getProjectMosaicByExtent(projectId: UUID, zoomOption: Option[Int],
    bboxOption: Option[String], colorCorrect: Boolean = true): OptionT[Future, MultibandTile] = {
    val bboxPolygon: Option[Projected[Polygon]] =
      try {
        bboxOption map { bbox =>
          Projected(Extent.fromString(bbox).toPolygon(), 4326)
            .reproject(LatLng, WebMercator)(3857)
        }
      } catch {
        case e: Exception =>
          throw new IllegalArgumentException(
            "Four comma separated coordinates must be given for bbox")
            .initCause(e)
      }

    val zoom: Int = zoomOption.getOrElse(8)
    val mosaicDefinitions = getMosaicDefinition(projectId)
    mosaicDefinitions.flatMap { mosaicDefinition =>
      val futureTiles: Future[Seq[MultibandTile]] = {

        val tiles = mosaicDefinition.flatMap {
          case MosaicDefinition(sceneId, maybeColorCorrectParams) =>
            maybeColorCorrectParams.map { colorCorrectParams =>
              val tlm = getSceneTileMetadata(sceneId, zoom)
              val renderedExtent = getSceneRenderedExtent(sceneId, zoom, bboxPolygon, tlm)

              renderedExtent.flatMap { tile =>
                if (colorCorrect) {
                  getSceneHistogram(sceneId, zoom).map { hist =>
                    colorCorrectParams.colorCorrect(tile, hist)
                  }
                } else {
                  OptionT[Future, MultibandTile](Future(Some(tile)))
                }

              }
                .value
            }.toSeq
        }
        Future.sequence(tiles).map(_.flatten)
      }

      val futureMergeTile =
        for {
          doColorCorrect <- hasColorCorrection(projectId, mosaicDefinitions)
          tiles <- futureTiles
        } yield colorCorrectAndMergeTiles(tiles, doColorCorrect)

      OptionT(futureMergeTile)
    }
  }

  /** Mosaic tiles from TMS pyramids given that they are in the same projection.
    *   If a layer does not go up to requested zoom it will be up-sampled.
    *   Layers missing color correction in the mosaic definition will be excluded.
    *
    * @param projectId
    */
  def getProjectMosaicByZXY(projectId: UUID, zoom: Int, col: Int, row: Int): OptionT[Future, MultibandTile] = {
    logger.debug(s"Creating mosaic (project: $projectId, zoom: $zoom, col: $col, row: $row)")
    val polygonBbox = TileUtils.getTileBounds(zoom, col, row)
    val md = getMosaicDefinition(projectId, Option(polygonBbox))
    md.flatMap { mosaic =>
      val futureTiles: Future[Seq[MultibandTile]] = {
        val tiles = mosaic.flatMap { case MosaicDefinition(sceneId, maybeColorCorrectParams) =>
          maybeColorCorrectParams.map { colorCorrectParams =>
            logger.debug(s"Getting Tile (project: $projectId, scene: $sceneId, col: $col, row: $row, zoom: $zoom)")
            val tile = getSceneTileZXY(sceneId, zoom, col, row)
            val computedHistogram = getSceneHistogram(sceneId, zoom)
            tile.flatMap { tile =>
              computedHistogram.map { hist =>
                colorCorrectParams.colorCorrect(tile, hist)
              }
            }.value
          }
        }
        Future.sequence(tiles).map(_.flatten)
      }

      val futureMergeTile = timedFuture("color-correct-merge-tiles")(
        for {
          doColorCorrect <- hasColorCorrection(projectId, md)
          tiles <- futureTiles
        } yield colorCorrectAndMergeTiles(tiles, doColorCorrect)
      )
      OptionT(futureMergeTile)
    }
  }

  /** Check to see if a project has color correction; if this isn't specified, default to false */
  def hasColorCorrection(projectId: UUID, mosaicDefinitions: OptionT[Future, Seq[MosaicDefinition]]) = {
    val autobalanceEnabled = mosaicDefinitions.map { mosaic =>
      mosaic.flatMap {
        case MosaicDefinition(_, maybeColorCorrectParams) => maybeColorCorrectParams.map(_.autoBalance.enabled)
      }.forall(identity)
    }.value

    autobalanceEnabled.map {
      case Some(true) => true
      case _ => false
    }
  }

  /** Merge tiles together, optionally color correcting */
  def colorCorrectAndMergeTiles(tiles: Seq[MultibandTile], doColorCorrect: Boolean): Option[MultibandTile] = {
    val newTiles = if (doColorCorrect) WhiteBalance(tiles.toList) else tiles
    if (newTiles.isEmpty) None else Some(newTiles.reduce(_ merge _))
  }

  /**
    *
    * @param sceneId
    * @param size
    * @return
    */
  def getStitchedScene(sceneId: UUID, size: Int): OptionT[Future, MultibandTile] = {
    rfCache.cachingOptionT(s"get-stitched-extent-${sceneId}-${size}")(OptionT(Future {
      require(size < 4096, s"$size is too large to stitch")
      findMinAcceptableSceneZoom(sceneId, size).map { case (re, zoom) =>
        logger.debug(s"Stitching from $sceneId, ${re.extent.reproject(WebMercator, LatLng).toGeoJson}")
        S3CollectionLayerReader(store)
          .query[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](LayerId(sceneId.toString, zoom))
          .where(Intersects(re.extent))
          .result
          .stitch
          .crop(re.extent)
      }
    }))
  }

  /**
    *
    * @param sceneId
    * @param projectId
    * @return
    */
  def getCorrectedHistogram(sceneId: UUID, projectId: UUID): OptionT[Future, Vector[Histogram[Int]]] = {
    val tileFuture = getStitchedScene(sceneId, 64)
    // getColorCorrectParams returns a Future[Option[Option]] for some reason
    val ccParamFuture = ScenesToProjects.getColorCorrectParams(projectId, sceneId).map { _.flatten }
    for {
      tile <- tileFuture
      params <- OptionT(ccParamFuture)
    } yield {
      val (rgbBands, rgbHist) = params.reorderBands(tile, tile.histogramDouble)
      val sceneBands = ColorCorrect(rgbBands, rgbHist, params).bands
      sceneBands.map(tile => tile.histogram)
    }
  }

}
