package com.rasterfoundry.backsplash.io

import com.rasterfoundry.database.util.RFTransactor

import cats.data.{OptionT, NonEmptyList => NEL}
import cats.effect.{IO, Timer}
import cats.implicits._
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.database.LayerAttributeDao
import com.rasterfoundry.datamodel.{MosaicDefinition, SingleBandOptions}
import com.rasterfoundry.backsplash.Color
import doobie.implicits._
import geotrellis.proj4.{WebMercator, LatLng}
import geotrellis.raster.histogram._
import geotrellis.raster.{Raster, io => _, _}
import geotrellis.spark.io._
import geotrellis.raster.io.json._
import geotrellis.spark.io.postgres.PostgresAttributeStore
import geotrellis.spark.io.s3.{S3CollectionLayerReader, S3ValueReader}
import geotrellis.spark.tiling.LayoutLevel
import geotrellis.spark.{SpatialKey, TileLayerMetadata, LayerId, io => _}
import geotrellis.vector.{Extent, Polygon, Projected}
import spray.json.DefaultJsonProtocol._
import spray.json._

import java.util.UUID

object Avro extends RollbarNotifier with HistogramJsonFormats {

  implicit val xa = RFTransactor.xa

  val store = PostgresAttributeStore()

  def fetchGlobalTile(
      mosaicDefinition: MosaicDefinition,
      extent: Option[Projected[Polygon]],
      redBand: Int,
      greenBand: Int,
      blueBand: Int,
      size: Int = 64,
      attributeStore: AttributeStore = store): IO[MultibandTile] = {
    require(size < 4096, s"$size is too large to stitch")
    minZoomLevel(attributeStore, mosaicDefinition.sceneId.toString, size).map {
      case (layerId, re) =>
        S3CollectionLayerReader(store)
          .query[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](
            layerId)
          .where(Intersects(re.extent))
          .result
          .stitch
          .crop(re.extent)
          .tile
          .subsetBands(redBand, greenBand, blueBand)
    }
  }

  def minZoomLevel(store: AttributeStore,
                   layerName: String,
                   size: Int): IO[(LayerId, RasterExtent)] = {
    def forZoom(zoom: Int, maxZoom: Int): IO[(LayerId, RasterExtent)] = {
      val currentId = LayerId(layerName, zoom)
      for {
        meta <- IO {
          store.readMetadata[TileLayerMetadata[SpatialKey]](currentId)
        }
        rasterExtent = dataRasterExtent(meta)
        result <- if (rasterExtent.cols >= size || rasterExtent.rows >= size || zoom == maxZoom) {
          IO.pure(currentId, rasterExtent)
        } else {
          forZoom(zoom + 1, maxZoom)
        }
      } yield result
    }

    for {
      maxZoom <- LayerAttributeDao.unsafeMaxZoomForLayer(layerName).transact(xa)
      result <- forZoom(1, maxZoom._2)
    } yield result
  }

  def dataRasterExtent(md: TileLayerMetadata[_]): RasterExtent = {
    val re = RasterExtent(md.layout.extent,
                          md.layout.tileLayout.totalCols.toInt,
                          md.layout.tileLayout.totalRows.toInt)
    val gb = re.gridBoundsFor(md.extent)
    re.rasterExtentFor(gb).toRasterExtent
  }

  // TODO: this essentially inlines a bunch of logic from LayerCache, which isn't super cool
  // it would be nice to get that logic somewhere more appropriate, especially since a lot of
  // it is grid <-> geometry math, but I'm not certain where it should go.
  def fetchMultiBandAvroTile(md: MosaicDefinition,
                             zoom: Int,
                             col: Int,
                             row: Int,
                             extent: Extent): OptionT[IO, Raster[Tile]] = {
    OptionT(
      for {
        _ <- IO.pure(
          logger.debug(
            s"Fetching multi-band avro tile for scene id ${md.sceneId}"))
        metadata <- tileLayerMetadata(md.sceneId, zoom)
        (sourceZoom, tlm) = metadata
        zoomDiff = zoom - sourceZoom
        resolutionDiff = 1 << zoomDiff
        sourceKey = SpatialKey(col / resolutionDiff, row / resolutionDiff)
        histograms <- layerHistogram(md.sceneId)
        mbTileE <- {
          if (tlm.bounds.includes(sourceKey))
            avroLayerTile(md.sceneId, sourceZoom, sourceKey).attempt
          else
            IO(
              Left(
                new Exception(
                  s"Source key outside of tile layer bounds for scene ${md.sceneId}, key ${sourceKey}")
              )
            )
        }
      } yield {
        (mbTileE map {
          (mbTile: MultibandTile) =>
            {
              val innerCol = col % resolutionDiff
              val innerRow = row % resolutionDiff
              val cols = mbTile.cols / resolutionDiff
              val rows = mbTile.rows / resolutionDiff
              val corrected =
                md.colorCorrections.colorCorrect(mbTile, histograms.toSeq, None)
              Raster(corrected.color, extent).resample(256, 256)
            }
        }).toOption
      }
    )
  }

  def fetchMultiBandAvroTile(
      md: MosaicDefinition,
      layoutLevel: LayoutLevel,
      extent: Extent): OptionT[IO, Raster[MultibandTile]] = {
    val layerId = LayerId(md.sceneId.toString, layoutLevel.zoom)
    val tileIO = IO {
      S3CollectionLayerReader(store)
        .query[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](
          layerId)
        .where(Intersects(extent))
        .result
        .stitch
        .crop(extent)
        .tile
        .resample(256, 256)
    } map { tile =>
      Some(Raster(tile, extent))
    } handleErrorWith {
      case e =>
        logger.error(
          s"Query layer ${layerId} at zoom ${layoutLevel.zoom}for $extent: ${e.getMessage}")
        IO { None }
    }
    OptionT(tileIO)
  }

  def fetchSingleBandAvroTile(
      md: MosaicDefinition,
      zoom: Int,
      col: Int,
      row: Int,
      extent: Extent,
      singleBandOptions: SingleBandOptions.Params,
      rawSingleBandValues: Boolean): OptionT[IO, Raster[Tile]] = {
    OptionT(
      for {
        _ <- IO.pure(
          logger.debug(
            s"Fetching single-band avro tile for scene id ${md.sceneId}"))
        metadata <- tileLayerMetadata(md.sceneId, zoom)
        (sourceZoom, tlm) = metadata
        zoomDiff = zoom - sourceZoom
        resolutionDiff = 1 << zoomDiff
        sourceKey = SpatialKey(col / resolutionDiff, row / resolutionDiff)
        histograms <- layerHistogram(md.sceneId)
        mbTileE <- {
          if (tlm.bounds.includes(sourceKey))
            avroLayerTile(md.sceneId, sourceZoom, sourceKey).attempt
          else
            IO(
              Left(
                new Exception(
                  s"Source key outside of tile layer bounds for scene ${md.sceneId}, key ${sourceKey}")
              )
            )
        }
      } yield {
        (mbTileE map {
          (mbTile: MultibandTile) =>
            {
              val tile = mbTile.bands.lift(singleBandOptions.band) getOrElse {
                throw new Exception("No band found in single-band options")
              }
              val histogram = histograms
                .lift(singleBandOptions.band) getOrElse {
                throw new Exception("No histogram found for band")
              }
              rawSingleBandValues match {
                case false =>
                  Color.colorSingleBandTile(tile,
                                            extent,
                                            histogram,
                                            singleBandOptions)
                case _ => Raster(tile, extent)
              }
            }
        }).toOption
      }
    )
  }

  def tileLayerMetadata(id: UUID,
                        zoom: Int): IO[(Int, TileLayerMetadata[SpatialKey])] = {
    val layerName = id.toString
    LayerAttributeDao.unsafeMaxZoomForLayer(layerName).transact(xa) map {
      case (_, maxZoom) =>
        val z = if (zoom > maxZoom) maxZoom else zoom
        z -> store.readMetadata[TileLayerMetadata[SpatialKey]](
          LayerId(layerName, z))
    }
  }

  def layerHistogram(id: UUID): IO[Array[Histogram[Double]]] = {
    val layerId = LayerId(name = id.toString, zoom = 0)
    LayerAttributeDao
      .unsafeGetAttribute(layerId, "histogram")
      .transact(xa) map { attribute =>
      attribute.value.noSpaces.parseJson.convertTo[Array[Histogram[Double]]]
    }
  }

  def avroLayerTile(id: UUID, zoom: Int, key: SpatialKey): IO[MultibandTile] = {
    val reader = new S3ValueReader(store)
      .reader[SpatialKey, MultibandTile](LayerId(id.toString, zoom))
    IO(reader.read(key))
  }

  def tileForExtent(
      extent: Extent,
      cellSize: CellSize,
      singleBandOptions: Option[SingleBandOptions.Params],
      singleBand: Boolean,
      mosaicDefinition: MosaicDefinition): IO[Option[Raster[Tile]]] = {
    val reprojectedExtent = extent.reproject(LatLng, WebMercator)
    val resampleRows = (reprojectedExtent.height / cellSize.height).toInt
    val resampleCols = (reprojectedExtent.width / cellSize.width).toInt
    for {
      minZoomAndExtent <- minZoomLevel(store,
                                       mosaicDefinition.sceneId.toString,
                                       resampleCols)
      (layerId, rasterExtent) = minZoomAndExtent
      hists <- layerHistogram(mosaicDefinition.sceneId)
      stitched <- IO {
        S3CollectionLayerReader(store)
          .query[SpatialKey, MultibandTile, TileLayerMetadata[SpatialKey]](
            layerId)
          .where(Intersects(reprojectedExtent))
          .result
          .stitch
          .crop(reprojectedExtent)
      }
      resampled = stitched.resample(resampleCols, resampleRows)
    } yield {
      if (!singleBand) {
        Some(
          Raster(mosaicDefinition.colorCorrections
                   .colorCorrect(resampled.tile, hists, None)
                   .color,
                 extent))
      } else {
        singleBandOptions flatMap { params =>
          resampled.tile.bands lift params.band map { tile =>
            Color.colorSingleBandTile(
              tile,
              resampled.extent,
              hists(params.band),
              params
            )
          }
        }
      }
    }
  }
}
