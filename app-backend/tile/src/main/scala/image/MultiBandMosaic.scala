package com.azavea.rf.tile.image

import com.azavea.rf.tile._
import com.azavea.rf.database.{SceneToProjectDao}
import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.datamodel.{MosaicDefinition, WhiteBalance}
import com.azavea.rf.common.cache.CacheClient
import geotrellis.raster._
import geotrellis.raster.io.geotiff.MultibandGeoTiff
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.raster.GridBounds
import geotrellis.proj4._
import geotrellis.slick.Projected
import geotrellis.vector.{Extent, Point, Polygon}
import cats.data._
import cats.implicits._
import cats.effect.IO
import java.util.UUID
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor


import com.azavea.rf.common.utils.TileUtils
import com.azavea.rf.database.util.RFTransactor

import scala.concurrent._
import scala.concurrent.duration._
import geotrellis.spark.io.postgres.PostgresAttributeStore
import com.azavea.rf.tile.AkkaSystem
import com.typesafe.scalalogging.LazyLogging

object MultiBandMosaic extends LazyLogging with KamonTrace {
  lazy val memcachedClient = LayerCache.memcachedClient
  val system = AkkaSystem.system
  implicit val blockingDispatcher =
    system.dispatchers.lookup("blocking-dispatcher")
  implicit lazy val xa = RFTransactor.xa

  val store = PostgresAttributeStore()
  val rfCache = new CacheClient(memcachedClient)

  def tileLayerMetadata(id: UUID, zoom: Int)(implicit xa: Transactor[IO],
    sceneIds: Set[UUID]): OptionT[Future, (Int, TileLayerMetadata[SpatialKey])] = {

    logger.debug(s"Requesting tile layer metadata (layer: $id, zoom: $zoom")
    LayerCache.maxZoomForLayers(Set(id)).mapFilter {
      case (pyramidMaxZoom) =>
        val layerName = id.toString
        for (maxZoom <- pyramidMaxZoom.get(layerName)) yield {
          val z = if (zoom > maxZoom) maxZoom else zoom
          blocking {
            z -> store.readMetadata[TileLayerMetadata[SpatialKey]](LayerId(layerName, z))
          }
        }
    }
  }

  def mosaicDefinition(projectId: UUID, polygonOption: Option[Projected[Polygon]])(
    implicit xa: Transactor[IO]): Future[Seq[MosaicDefinition]] = {

    logger.debug(s"Reading mosaic definition (project: $projectId")
    SceneToProjectDao.getMosaicDefinition(projectId, polygonOption).transact(xa).unsafeToFuture
  }

  def mosaicDefinition(projectId: UUID)(
    implicit xa: Transactor[IO]): Future[Seq[MosaicDefinition]] = {
    mosaicDefinition(projectId, None)
  }

  /** Fetch the tile for given resolution. If it is not present, use a tile from a lower zoom level */
  def fetch(id: UUID, zoom: Int, col: Int, row: Int)(
      implicit xa: Transactor[IO],
      sceneIds: Set[UUID]): OptionT[Future, MultibandTile] = {
    tileLayerMetadata(id, zoom).flatMap {
      case (sourceZoom, tlm) =>
        val zoomDiff = zoom - sourceZoom
        logger.debug(s"Requesting layer tile (layer: $id, zoom: $zoom, col: $col, row: $row, sourceZoom: $sourceZoom)")
        val resolutionDiff = 1 << zoomDiff
        val sourceKey = SpatialKey(col / resolutionDiff, row / resolutionDiff)
        if (tlm.bounds.includes(sourceKey)) {
          LayerCache.layerTile(id, sourceZoom, sourceKey).map { tile =>
            val innerCol = col % resolutionDiff
            val innerRow = row % resolutionDiff
            val cols = tile.cols / resolutionDiff
            val rows = tile.rows / resolutionDiff
            tile.crop(
              GridBounds(
                colMin = innerCol * cols,
                rowMin = innerRow * rows,
                colMax = (innerCol + 1) * cols - 1,
                rowMax = (innerRow + 1) * rows - 1
              )
            ).resample(256, 256)
          }
        } else {
          OptionT.none[Future, MultibandTile]
        }
    }
  }

  /** Fetch the tile for the given zoom level and bbox
    * If no bbox is specified, it will use the project tileLayerMetadata layoutExtent
    */
  def fetchRenderedExtent(id: UUID, zoom: Int,  bbox: Option[Projected[Polygon]])(
    implicit xa: Transactor[IO], sceneIds: Set[UUID]): OptionT[Future, MultibandTile] =
    tileLayerMetadata(id, zoom).flatMap {
      case (sourceZoom, tlm) =>
        val extent: Extent =
          bbox
            .map {
              case Projected(poly, srid) =>
                poly.envelope.reproject(CRS.fromEpsgCode(srid), tlm.crs)
            }
            .getOrElse(tlm.layoutExtent)

        LayerCache.layerTileForExtent(id, sourceZoom, extent)
    }

  /** Fetch all bands of a [[MultibandTile]] for the given extent and return them without assuming anything of their semantics */
  def rawForExtent(projectId: UUID,
                   zoom: Int,
                   bbox: Option[Projected[Polygon]])(
                   implicit xa: Transactor[IO]): OptionT[Future, MultibandTile] = {
    OptionT(mosaicDefinition(projectId).flatMap { mosaic =>
      implicit val sceneIds = mosaic.map {
        case MosaicDefinition(sceneId, _) => sceneId
      }.toSet

      val mayhapTiles: Seq[OptionT[Future, MultibandTile]] =
        for (MosaicDefinition(sceneId, _) <- mosaic)
          yield MultiBandMosaic.fetchRenderedExtent(sceneId, zoom, bbox)

      val futureMergeTile: Future[Option[MultibandTile]] =
        Future.sequence(mayhapTiles.map(_.value)).map { maybeTiles =>
          val tiles = maybeTiles.flatten
          if (tiles.nonEmpty)
            Option(tiles.reduce(_ merge _))
          else
            Option.empty[MultibandTile]
        }

      futureMergeTile
    })
  }


  /** Fetch all bands of a [[MultibandTile]] and return them without assuming anything of their semantics */
  def raw(projectId: UUID, zoom: Int, col: Int, row: Int)(
    implicit xa: Transactor[IO]): OptionT[Future, MultibandTile] =
    OptionT(mosaicDefinition(projectId).flatMap { mosaic =>
      implicit val sceneIds = mosaic.map {
        case MosaicDefinition(sceneId, _) => sceneId
      }.toSet

      val mayhapTiles: Seq[OptionT[Future, MultibandTile]] =
        for (MosaicDefinition(sceneId, _) <- mosaic)
          yield MultiBandMosaic.fetch(sceneId, zoom, col, row)

      val futureMergeTile: Future[Option[MultibandTile]] =
        Future.sequence(mayhapTiles.map(_.value)).map { maybeTiles =>
          val tiles = maybeTiles.flatten
          if (tiles.nonEmpty)
            Option(tiles.reduce(_ merge _))
          else
            Option.empty[MultibandTile]
        }

      futureMergeTile
    })

  /**   Render a multiband tile from TMS pyramids given that they are in the same projection.
    *   If a layer does not go up to requested zoom it will be up-sampled.
    *   Layers missing color correction in the mosaic definition will be excluded.
    *   The size of the image will depend on the selected zoom and bbox.
    *
    *   Note:
    *   Currently, if the render takes too long, it will time out. Given enough requests, this
    *   could cause us to essentially ddos ourselves, so we probably want to change
    *   this from a simple endpoint to a batch operation: IE the request kicks off
    *   a render job then returns the job id
    *
    *   @param zoomOption  the zoom level to use
    *   @param bboxOption the bounding box for the image
    *   @param colorCorrect setting to determine if color correction should be applied
    */
  def render(projectId: UUID,
             zoomOption: Option[Int],
             bboxOption: Option[String],
             colorCorrect: Boolean = true)(
      implicit xa: Transactor[IO]): OptionT[Future, MultibandTile] = {
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
    val md: Future[Seq[MosaicDefinition]] = mosaicDefinition(projectId)
    val mt: Future[Option[MultibandTile]] = md.flatMap { mosaic =>
      val futureTiles: Future[Seq[MultibandTile]] = {
        implicit val sceneIds = mosaic.map {
          case MosaicDefinition(sceneId, _) => sceneId
        }.toSet

        val tiles = mosaic.flatMap {
          case MosaicDefinition(sceneId, maybeColorCorrectParams) =>
            maybeColorCorrectParams.map { colorCorrectParams =>
              MultiBandMosaic
                .fetchRenderedExtent(sceneId, zoom, bboxPolygon)
                .flatMap { tile =>
                  if (colorCorrect) {
                    LayerCache.layerHistogram(sceneId, zoom).map { hist =>
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
          doColorCorrect <- hasColorCorrection(projectId, md)
          tiles <- futureTiles
        } yield colorCorrectAndMergeTiles(tiles, doColorCorrect)

      futureMergeTile
    }
    OptionT(mt)
  }

  /** MultiBandMosaic tiles from TMS pyramids given that they are in the same projection.
    *   If a layer does not go up to requested zoom it will be up-sampled.
    *   Layers missing color correction in the mosaic definition will be excluded.
    */
  def apply(
      projectId: UUID,
      zoom: Int,
      col: Int,
      row: Int
  )(
      implicit xa: Transactor[IO]
  ): OptionT[Future, MultibandTile] = traceName(s"MultiBandMosaic.apply($projectId)") {
    logger.debug(s"Creating mosaic (project: $projectId, zoom: $zoom, col: $col, row: $row)")
    val polygonBbox: Projected[Polygon] = TileUtils.getTileBounds(zoom, col, row)
    val md: Future[Seq[MosaicDefinition]] = mosaicDefinition(projectId, Option(polygonBbox))
    md.flatMap { (mosaic: Seq[MosaicDefinition]) =>
      val futureTiles: Future[Seq[MultibandTile]] = {
        implicit val sceneIds = mosaic.map {
          case MosaicDefinition(sceneId, _) => sceneId
        }.toSet

        val tiles = mosaic.flatMap { case MosaicDefinition(sceneId, maybeColorCorrectParams) =>
          maybeColorCorrectParams.map { colorCorrectParams =>
            logger.debug(s"Getting Tile (project: $projectId, scene: $sceneId, col: $col, row: $row, zoom: $zoom)")
            val tile = rfCache.cachingOptionT(s"tile-${sceneId}-${zoom}-${col}-${row}")(
              MultiBandMosaic.fetch(sceneId, zoom, col, row)
            )
            val layerHistogram = LayerCache.layerHistogram(sceneId, zoom)
            tile.flatMap { tile =>
              layerHistogram.map { hist =>
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
  def hasColorCorrection(projectId: UUID,
                         md: Future[Seq[MosaicDefinition]])(
      implicit xa: Transactor[IO]): Future[Boolean] =
    md.flatMap { _.collectFirst {
        case MosaicDefinition(sceneId, Some(maybeColorCorrectParams)) =>
          maybeColorCorrectParams.autoBalance.enabled.getOrElse(false)
      }
    }

  /** Merge tiles together, optionally color correcting */
  def colorCorrectAndMergeTiles(
      tiles: Seq[MultibandTile],
      doColorCorrect: Boolean): Option[MultibandTile] =
    traceName("MultiBandMosaic.colorCorrectAndMergeTiles") {
      val newTiles =
        if (doColorCorrect)
          WhiteBalance(tiles.toList)
        else
          tiles

      if (newTiles.isEmpty)
        None
      else
        Some(newTiles.reduce(_ merge _))
    }
}
