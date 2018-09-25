package com.azavea.rf.tile.image

import com.azavea.rf.tile._
import com.azavea.rf.database.{SceneToProjectDao, SceneDao}
import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel.{
  ColorCorrect,
  MosaicDefinition,
  SceneType,
  WhiteBalance
}
import com.azavea.rf.common.cache.CacheClient
import geotrellis.raster._
import geotrellis.raster.io.geotiff.MultibandGeoTiff
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.raster.GridBounds
import geotrellis.proj4._
import geotrellis.vector.{Extent, Point, Polygon, Projected}
import cats.data._
import cats.implicits._
import cats.effect.IO
import java.util.UUID

import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import com.azavea.rf.common.utils.{
  CogUtils,
  CryptoUtils,
  RangeReaderUtils,
  TileUtils
}
import com.azavea.rf.database.util.RFTransactor

import scala.concurrent._
import scala.concurrent.duration._
import geotrellis.spark.io.postgres.PostgresAttributeStore
import com.azavea.rf.tile.AkkaSystem
import com.typesafe.scalalogging.LazyLogging
import geotrellis.raster.io.geotiff.reader.GeoTiffReader

object MultiBandMosaic extends LazyLogging with KamonTrace {
  lazy val memcachedClient = LayerCache.memcachedClient
  val system = AkkaSystem.system
  implicit val blockingDispatcher =
    system.dispatchers.lookup("blocking-dispatcher")
  implicit lazy val xa = RFTransactor.xa

  val store = PostgresAttributeStore()
  val rfCache = new CacheClient(memcachedClient)

  def tileLayerMetadata(id: UUID, zoom: Int)(
      implicit xa: Transactor[IO]
  ): OptionT[Future, (Int, TileLayerMetadata[SpatialKey])] = {

    logger.debug(s"Requesting tile layer metadata (layer: $id, zoom: $zoom")
    LayerCache.maxZoomForLayers(Set(id)).mapFilter {
      case (pyramidMaxZoom) =>
        val layerName = id.toString
        for (maxZoom <- pyramidMaxZoom.get(layerName)) yield {
          val z = if (zoom > maxZoom) maxZoom else zoom
          blocking {
            z -> store.readMetadata[TileLayerMetadata[SpatialKey]](
              LayerId(layerName, z))
          }
        }
    }
  }

  def mosaicDefinition(projectId: UUID,
                       polygonOption: Option[Projected[Polygon]] = None)(
      implicit xa: Transactor[IO]): Future[Seq[MosaicDefinition]] = {

    logger.debug(s"Reading mosaic definition (project: $projectId")
    SceneToProjectDao
      .getMosaicDefinition(projectId, polygonOption)
      .transact(xa)
      .unsafeToFuture
  }

  def mosaicDefinition(id: UUID,
                       polygonOption: Option[Projected[Polygon]],
                       isScene: Boolean)(
      implicit xa: Transactor[IO]): Future[Seq[MosaicDefinition]] = {
    if (isScene) {
      logger.debug(s"Reading mosaic definition (scene: $id")
      SceneDao
        .getMosaicDefinition(id, polygonOption)
        .transact(xa)
        .unsafeToFuture
    } else
      (
        mosaicDefinition(id,
                         polygonOption)
      )
  }

  def mosaicDefinition(projectId: UUID,
                       polygonOption: Option[Projected[Polygon]],
                       redBand: Int,
                       greenBand: Int,
                       blueBand: Int)(
      implicit xa: Transactor[IO]): Future[Seq[MosaicDefinition]] = {
    SceneToProjectDao
      .getMosaicDefinition(projectId,
                           polygonOption,
                           Some(redBand),
                           Some(greenBand),
                           Some(blueBand))
      .transact(xa)
      .unsafeToFuture
  }

  /** Fetch the tile for given resolution. If it is not present, use a tile from a lower zoom level */
  def fetch(id: UUID,
            zoom: Int,
            col: Int,
            row: Int,
            sceneType: Option[SceneType],
            ingestLocation: Option[String])(
      implicit xa: Transactor[IO]): OptionT[Future, MultibandTile] = {
    (sceneType, ingestLocation) match {
      case (Some(SceneType.COG), Some(cogUri)) =>
        CogUtils.fetch(cogUri, zoom, col, row)
      case (_, _) => fetchAvroTile(id, zoom, col: Int, row: Int)
    }
  }

  def fetchAvroTile(id: UUID, zoom: Int, col: Int, row: Int)(
      implicit xa: Transactor[IO]) = {
    tileLayerMetadata(id, zoom).flatMap {
      case (sourceZoom, tlm) =>
        val zoomDiff = zoom - sourceZoom
        logger.debug(
          s"Requesting layer tile (layer: $id, zoom: $zoom, col: $col, row: $row, sourceZoom: $sourceZoom)")
        val resolutionDiff = 1 << zoomDiff
        val sourceKey = SpatialKey(col / resolutionDiff, row / resolutionDiff)
        if (tlm.bounds.includes(sourceKey)) {
          LayerCache.layerTile(id, sourceZoom, sourceKey).map { tile =>
            val innerCol = col % resolutionDiff
            val innerRow = row % resolutionDiff
            val cols = tile.cols / resolutionDiff
            val rows = tile.rows / resolutionDiff
            tile
              .crop(
                GridBounds(
                  colMin = innerCol * cols,
                  rowMin = innerRow * rows,
                  colMax = (innerCol + 1) * cols - 1,
                  rowMax = (innerRow + 1) * rows - 1
                )
              )
              .resample(256, 256)
          }
        } else {
          OptionT.none[Future, MultibandTile]
        }
    }
  }

  /** Fetch the tile for the given zoom level and bbox
    * If no bbox is specified, it will use the project tileLayerMetadata layoutExtent
    */
  def fetchRenderedExtent(id: UUID,
                          zoom: Int,
                          bbox: Option[Projected[Polygon]])(
      implicit xa: Transactor[IO]): OptionT[Future, MultibandTile] =
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
                   bbox: Option[Projected[Polygon]],
                   doColorCorrect: Boolean)(
      implicit xa: Transactor[IO]): OptionT[Future, MultibandTile] = {
    OptionT(mosaicDefinition(projectId).flatMap { mosaic =>
      val sceneIds = mosaic.map {
        case MosaicDefinition(sceneId, _, _, _) => sceneId
      }.toSet

      val mayhapTiles: Future[Seq[MultibandTile]] =
        MultiBandMosaic.renderForBbox(Future { mosaic },
                                      bbox,
                                      zoom,
                                      None,
                                      doColorCorrect)

      val futureMergeTile: Future[Option[MultibandTile]] =
        mayhapTiles.map { tiles =>
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
      val sceneIds = mosaic.map {
        case MosaicDefinition(sceneId, _, _, _) => sceneId
      }.toSet

      val mayhapTiles: Seq[OptionT[Future, MultibandTile]] =
        for (MosaicDefinition(sceneId, _, sceneType, ingestLocation) <- mosaic)
          yield
            MultiBandMosaic.fetch(sceneId,
                                  zoom,
                                  col,
                                  row,
                                  sceneType,
                                  ingestLocation)

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
  def render(
      projectId: UUID,
      zoomOption: Option[Int],
      bboxOption: Option[String],
      colorCorrect: Boolean = true
  )(
      implicit xa: Transactor[IO]
  ): OptionT[Future, MultibandTile] = {
    val polygonBbox: Option[Projected[Polygon]] =
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

    OptionT(
      mergeTiles(
        renderForBbox(md, polygonBbox, zoom, None)
      )
    )
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
  ): OptionT[Future, MultibandTile] =
    traceName(s"MultiBandMosaic.apply($projectId)") {
      logger.debug(
        s"Creating mosaic (project: $projectId, zoom: $zoom, col: $col, row: $row)")

      val polygonBbox: Projected[Polygon] =
        TileUtils.getTileBounds(zoom, col, row)
      val md: Future[Seq[MosaicDefinition]] =
        mosaicDefinition(projectId, Option(polygonBbox))
      OptionT(
        mergeTiles(
          renderForBbox(md,
                        Some(polygonBbox),
                        zoom,
                        Some(s"${zoom}-${col}-${row}"))
        )
      )
    }

  def apply(
      id: UUID,
      zoom: Int,
      col: Int,
      row: Int,
      isScene: Boolean
  )(implicit xa: Transactor[IO]): OptionT[Future, MultibandTile] =
    traceName(s"MultiBandMosaic.apply($id)") {
      if (isScene) {
        logger.debug(
          s"Creating mosaic (scene: $id, zoom: $zoom, col: $col, row: $row)")
        val polygonBbox: Projected[Polygon] =
          TileUtils.getTileBounds(zoom, col, row)
        val md: Future[Seq[MosaicDefinition]] =
          mosaicDefinition(id, Option(polygonBbox), true)
        OptionT(
          mergeTiles(
            renderForBbox(md,
                          Some(polygonBbox),
                          zoom,
                          Some(s"${zoom}-${col}-${row}"),
                          false)))
      } else {
        apply(id, zoom, col, row)
      }
    }

  def apply(
      projectId: UUID,
      zoom: Int,
      col: Int,
      row: Int,
      redBand: Int,
      greenBand: Int,
      blueBand: Int
  )(implicit xa: Transactor[IO]): OptionT[Future, MultibandTile] =
    traceName(s"MultiBandMosaic.apply($projectId)") {
      logger.debug(
        s"Creating mosaic (project: $projectId, zoom: $zoom, col: $col, row: $row)")

      val polygonBbox: Projected[Polygon] =
        TileUtils.getTileBounds(zoom, col, row)
      val md: Future[Seq[MosaicDefinition]] =
        mosaicDefinition(projectId,
                         Option(polygonBbox),
                         redBand,
                         greenBand,
                         blueBand)
      OptionT(
        mergeTiles(
          renderForBbox(md,
                        Some(polygonBbox),
                        zoom,
                        Some(s"${zoom}-${col}-${row}"))
        )
      )
    }

  def renderForBbox(
      mds: Future[Seq[MosaicDefinition]],
      bbox: Option[Projected[Polygon]],
      zoom: Int,
      cacheKey: Option[String],
      colorCorrect: Boolean = true
  ): Future[Seq[MultibandTile]] =
    mds.flatMap { mosaic: Seq[MosaicDefinition] =>
      {
        val sceneIds = mosaic.map {
          case MosaicDefinition(sceneId, _, _, _) => sceneId
        }.toSet

        val tiles = mosaic.map {
          case MosaicDefinition(sceneId,
                                colorCorrectParams,
                                sceneType,
                                maybeIngestLocation) => {
            val tile = (maybeIngestLocation, sceneType) match {
              case (Some(ingestLocation), Some(SceneType.COG)) =>
                fetchCogTile(bbox,
                             zoom,
                             colorCorrect,
                             sceneId,
                             colorCorrectParams,
                             ingestLocation)
              case (Some(ingestLocation), _) => {
                MultiBandMosaic
                  .fetchRenderedExtent(sceneId, zoom, bbox)
                  .flatMap { tile =>
                    if (colorCorrect) {
                      LayerCache.layerHistogram(sceneId, zoom).map { hist =>
                        colorCorrectParams.colorCorrect(tile, hist)
                      }
                    } else {
                      OptionT[Future, MultibandTile](Future(Some(tile)))
                    }
                  }
              }
              case _ => OptionT.none[Future, MultibandTile]
            }
            cacheKey match {
              case Some(key) =>
                rfCache.cachingOptionT(s"tile-rendered-${sceneId}-${key}")(tile)
              case _ =>
            }
            tile
          }
        }
        Future.traverse(tiles)(_.value).map(_.flatten)
      }
    }

  def fetchCogTile(bbox: Option[Projected[Polygon]],
                   zoom: Int,
                   colorCorrect: Boolean,
                   sceneId: UUID,
                   colorCorrectParams: ColorCorrect.Params,
                   ingestLocation: String): OptionT[Future, MultibandTile] = {

    val extent: Option[Extent] = bbox.map { poly =>
      poly.geom.envelope
    }

    val cacheKey = extent match {
      case Some(e) =>
        s"scene-bbox-${sceneId}-${zoom}-${e.xmin}-${e.ymin}-${e.xmax}-${e.ymax}-${CryptoUtils
          .sha1(colorCorrectParams.toString)}"
      case _ =>
        s"scene-bbox-${sceneId}-${zoom}-no-extent-${CryptoUtils.sha1(colorCorrectParams.toString)}"
    }

    rfCache.cachingOptionT(cacheKey)(
      CogUtils.fromUri(ingestLocation).flatMap { tiff =>
        CogUtils.cropForZoomExtent(tiff, zoom, extent).flatMap {
          cropped: MultibandTile =>
            if (colorCorrect) {
              rfCache
                .cachingOptionT(s"hist-tiff-${sceneId}") {
                  OptionT.liftF(Future(CogUtils.geoTiffHistogram(tiff)))
                }
                .semiflatMap { histogram =>
                  Future(colorCorrectParams.colorCorrect(cropped, histogram))
                }
            } else OptionT.pure[Future](cropped)
        }
      }
    )

  }

  def mergeTiles(
      tiles: Future[Seq[MultibandTile]]): Future[Option[MultibandTile]] = {
    tiles.map(_.reduceOption(_ merge _))
  }
}
