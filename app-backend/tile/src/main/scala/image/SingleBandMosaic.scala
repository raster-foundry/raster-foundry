package com.azavea.rf.tile.image

import com.azavea.rf.common.utils.{CogUtils, TileUtils}
import com.azavea.rf.common.cache.CacheClient
import com.azavea.rf.database.Implicits._
import com.azavea.rf.database.{SceneDao, SceneToProjectDao}
import com.azavea.rf.datamodel.{
  ColorRampMosaic,
  MosaicDefinition,
  Project,
  SceneType,
  SingleBandOptions
}
import com.azavea.rf.tile._
import com.typesafe.scalalogging.LazyLogging
import cats.data._
import cats.implicits._
import cats.effect.IO
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.GridBounds
import geotrellis.raster.histogram._
import geotrellis.raster.resample._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.postgres.PostgresAttributeStore
import geotrellis.vector.{Extent, Polygon, Projected}

import scala.concurrent._
import java.util.UUID

import com.azavea.rf.database.util.RFTransactor

object SingleBandMosaic extends LazyLogging with KamonTrace {
  lazy val memcachedClient = LayerCache.memcachedClient
  implicit val xa = RFTransactor.xa
  val system = AkkaSystem.system
  implicit val blockingDispatcher =
    system.dispatchers.lookup("blocking-dispatcher")

  val store = PostgresAttributeStore()
  val rfCache = new CacheClient(memcachedClient)

  def tileLayerMetadata(id: UUID, zoom: Int)(
      implicit xa: Transactor[IO],
      sceneIds: Set[UUID]
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

  def fetchCogTileWithHist(bbox: Option[Projected[Polygon]],
                           zoom: Int,
                           sceneId: UUID,
                           ingestLocation: String)
    : Future[Option[(MultibandTile, Array[Histogram[Double]])]] = {

    val extent: Option[Extent] = bbox.map { poly =>
      poly.geom.envelope
    }

    val cacheKey = extent match {
      case Some(e) =>
        s"scene-bbox-${sceneId}-${zoom}-${e.xmin}-${e.ymin}-${e.xmax}-${e.ymax}-single-band}"
      case _ => s"scene-bbox-${sceneId}-${zoom}-no-extent-single-band}"
    }

    rfCache
      .cachingOptionT(cacheKey)(
        CogUtils.fromUri(ingestLocation).flatMap { tiff =>
          CogUtils.cropForZoomExtent(tiff, zoom, extent).flatMap {
            cropped: MultibandTile =>
              val hist = CogUtils.geoTiffDoubleHistogram(tiff)
              OptionT.pure[Future]((cropped, hist))
          }
        }
      )
      .value
  }

  /** SingleBandMosaic tiles from TMS pyramids given that they are in the same projection.
    *   If a layer does not go up to requested zoom it will be up-sampled.
    *   Layers missing color correction in the mosaic definition will be excluded.
    */
  def apply(
      project: Project,
      zoom: Int,
      col: Int,
      row: Int
  )(implicit xa: Transactor[IO]): OptionT[Future, MultibandTile] =
    traceName(s"SingleBandMosaic.apply(${project.id})") {
      logger.debug(
        s"Creating single band mosaic (project: ${project.id}, zoom: $zoom, col: $col, row: $row)")

      val polygonBbox: Projected[Polygon] =
        TileUtils.getTileBounds(zoom, col, row)
      project.singleBandOptions match {
        case Some(singleBandOptions: SingleBandOptions.Params) => {
          val futureTiles
            : Future[Seq[(MultibandTile, Array[Histogram[Double]])]] = {
            SceneToProjectDao
              .getMosaicDefinition(project.id, Some(polygonBbox))
              .transact(xa)
              .unsafeToFuture
              .map { mds =>
                val sceneIds = mds.map {
                  case MosaicDefinition(sceneId, _, _, _) => sceneId
                }.toSet

                Future
                  .sequence(mds.map { md =>
                    (md.sceneType, md.ingestLocation) match {
                      case (Some(SceneType.COG), Some(loc)) => {
                        fetchCogTileWithHist(Some(polygonBbox),
                                             zoom,
                                             md.sceneId,
                                             loc)
                      }
                      case _ => {
                        OptionT(
                          SingleBandMosaic
                            .fetchAvroTile(md.sceneId, zoom, col, row)(xa,
                                                                       sceneIds)
                            .value
                            .zip(
                              LayerCache.layerHistogram(md.sceneId, zoom).value)
                            .map({
                              case (tile, histogram) =>
                                for (a <- tile; b <- histogram) yield (a, b)
                            })).value
                      }
                    }
                  })
                  .map(_.flatten)
              }
              .flatten
          }
          val futureColoredTile =
            for {
              tiles: Seq[(MultibandTile, Array[Histogram[Double]])] <- futureTiles
            } yield ColorRampMosaic(tiles.toList, singleBandOptions)
          OptionT(futureColoredTile)
        }
        case _ =>
          val message = "No valid single band render options found"
          logger.error(message)
          throw new IllegalArgumentException(message)
      }
    }

  def render(
      project: Project,
      zoomOption: Option[Int],
      bboxOption: Option[String],
      colorCorrect: Boolean
  )(implicit xa: Transactor[IO]): OptionT[Future, MultibandTile] =
    traceName(s"SingleBandMosaic.render(${project.id})") {
      val bboxPolygon: Option[Projected[Polygon]] =
        try {
          bboxOption map { bbox =>
            Projected(Extent.fromString(bbox).toPolygon(), 4326)
              .reproject(LatLng, WebMercator)(3857)
          }
        } catch {
          case e: Exception =>
            val message =
              "Four comma separated coordinates must be given for bbox"
            logger.error(message)
            throw new IllegalArgumentException(message).initCause(e)
        }
      val zoom: Int = zoomOption.getOrElse(8)
      project.singleBandOptions match {
        case Some(singleBandOptions: SingleBandOptions.Params) =>
          val futureTiles
            : Future[Seq[(MultibandTile, Array[Histogram[Double]])]] = {
            SceneToProjectDao
              .getMosaicDefinition(project.id, bboxPolygon)
              .transact(xa)
              .unsafeToFuture
              .map { mds =>
                val sceneIds = mds.map {
                  case MosaicDefinition(sceneId, _, _, _) => sceneId
                }.toSet

                Future
                  .sequence(mds.map { md =>
                    (md.sceneType, md.ingestLocation) match {
                      case (Some(SceneType.COG), Some(loc)) => {
                        fetchCogTileWithHist(bboxPolygon, zoom, md.sceneId, loc)
                      }
                      case _ => {
                        OptionT(
                          SingleBandMosaic
                            .fetchRenderedExtent(
                              md.sceneId,
                              zoom,
                              bboxPolygon,
                              singleBandOptions.band)(xa, sceneIds)
                            .value
                            .zip(
                              LayerCache.layerHistogram(md.sceneId, zoom).value)
                            .map({
                              case (tile, histogram) =>
                                for (a <- tile; b <- histogram) yield (a._1, b)
                            })).value
                      }
                    }
                  })
                  .map(_.flatten)
              }
              .flatten
          }

          colorCorrect match {
            case true =>
              val futureColoredRender = for {
                tiles <- futureTiles
              } yield ColorRampMosaic(tiles.toList, singleBandOptions)
              OptionT(futureColoredRender)
            case false =>
              def processTiles(
                  tiles: Seq[MultibandTile]): Option[MultibandTile] = {
                val singleBandTiles = tiles.map(_.band(singleBandOptions.band))
                singleBandTiles.isEmpty match {
                  case true =>
                    None
                  case _ =>
                    Some(MultibandTile(singleBandTiles.reduce(_ merge _)))
                }
              }

              val futureColoredRender = for {
                tiles: Seq[(MultibandTile, Array[Histogram[Double]])] <- futureTiles
              } yield processTiles(tiles.map(_._1))
              OptionT(futureColoredRender)
          }
        case None =>
          val message = "No valid single band render options found"
          logger.error(message)
          throw new IllegalArgumentException(message)
      }
    }

  def rawWithBandOverride(
      projectId: UUID,
      zoomOption: Option[Int],
      bboxPolygon: Option[Projected[Polygon]],
      bandOverride: Int
  )(implicit xa: Transactor[IO]): OptionT[Future, MultibandTile] =
    traceName(s"SingleBandMosaic.render(${projectId}-band-$bandOverride)") {
      val zoom: Int = zoomOption.getOrElse(8)
      val futureTiles
        : Future[Seq[(MultibandTile, Array[Histogram[Double]])]] = {
        SceneToProjectDao
          .getMosaicDefinition(projectId, bboxPolygon)
          .transact(xa)
          .unsafeToFuture
          .map { mds =>
            val sceneIds = mds.map {
              case MosaicDefinition(sceneId, _, _, _) => sceneId
            }.toSet

            Future
              .sequence(
                mds
                  .map {
                    md =>
                      (md.sceneType, md.ingestLocation) match {
                        case (Some(SceneType.COG), Some(loc)) => {
                          fetchCogTileWithHist(bboxPolygon,
                                               zoom,
                                               md.sceneId,
                                               loc) map {
                            case Some((mbTile, histArray)) =>
                              Some((MultibandTile(mbTile.band(bandOverride)),
                                    histArray))
                            case _ => None
                          }
                        }
                        case _ => {
                          OptionT(
                            SingleBandMosaic
                              .fetchRenderedExtent(md.sceneId,
                                                   zoom,
                                                   bboxPolygon,
                                                   bandOverride)(xa, sceneIds)
                              .value
                              .zip(LayerCache
                                .layerHistogram(md.sceneId, zoom)
                                .value)
                              .map({
                                case (tile, histogram) =>
                                  for (a <- tile; b <- histogram)
                                    yield (a._1, b)
                              })).value
                        }
                      }
                  })
              .map(_.flatten)
          }
          .flatten
      }

      OptionT.liftF(
        futureTiles map {
          (tiles: Seq[(MultibandTile, Array[Histogram[Double]])]) =>
            {
              tiles map { _._1 }
            } reduce {
              _.resample(256, 256, Average) merge _.resample(256, 256, Average)
            }
        }
      )
    }

  /** Fetch the tile for given resolution. If it is not present, use a tile from a lower zoom level */
  def fetchAvroTile(id: UUID, zoom: Int, col: Int, row: Int)(
      implicit xa: Transactor[IO],
      sceneIds: Set[UUID]
  ): OptionT[Future, MultibandTile] = {
    tileLayerMetadata(id, zoom).flatMap {
      case (sourceZoom, tlm) =>
        val zoomDiff = zoom - sourceZoom
        logger.debug(
          s"Requesting layer tile (layer: $id, zoom: $zoom, col: $col, row: $row, sourceZoom: $sourceZoom)")
        val resolutionDiff = 1 << zoomDiff
        val sourceKey = SpatialKey(col / resolutionDiff, row / resolutionDiff)
        if (tlm.bounds.includes(sourceKey)) {
          val tile = LayerCache.layerTile(id, sourceZoom, sourceKey).map {
            tile =>
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
          tile

        } else {
          OptionT.none[Future, MultibandTile]
        }
    }
  }

  def fetchRenderedExtent(
      id: UUID,
      zoom: Int,
      bbox: Option[Projected[Polygon]],
      band: Int)(implicit xa: Transactor[IO], sceneIds: Set[UUID])
    : OptionT[Future, (MultibandTile, Array[Histogram[Double]])] =
    tileLayerMetadata(id, zoom).flatMap {
      case (sourceZoom, tlm) =>
        val extent: Extent =
          bbox
            .map {
              case Projected(poly, srid) =>
                poly.envelope.reproject(CRS.fromEpsgCode(srid), tlm.crs)
            }
            .getOrElse(tlm.layoutExtent)
        val hist = LayerCache.layerHistogram(id, sourceZoom)
        val tile =
          LayerCache.layerSinglebandTileForExtent(id, sourceZoom, extent, band)
        for {
          t <- tile
          h <- hist
        } yield (MultibandTile(t), h)
    }
}
