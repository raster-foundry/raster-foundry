package com.azavea.rf.backsplash.nodes

import com.azavea.maml.ast.{Literal, MamlKind, RasterLit}
import com.azavea.rf.backsplash.error._
import com.azavea.rf.common.RollbarNotifier
import com.azavea.rf.database._
import com.azavea.rf.datamodel.{
  ColorRampMosaic,
  HistogramAttribute,
  LayerAttribute,
  MosaicDefinition,
  SceneType,
  SingleBandOptions
}

import cats.data.{OptionT, EitherT}
import cats.effect.{IO, Timer}
import cats.implicits._
import doobie.implicits._
import geotrellis.raster.{CellSize, CellType, Raster, GridBounds}
import geotrellis.raster.render.{ColorMap, ColorRamps}
import geotrellis.server.core.cog.CogUtils
import geotrellis.server.core.maml.CogNode
import geotrellis.server.core.maml.persistence._
import geotrellis.server.core.maml.metadata._
import geotrellis.server.core.maml.reification._
import geotrellis.raster.{io => _, _}
import geotrellis.raster.io.json.HistogramJsonFormats
import geotrellis.raster.histogram._
import geotrellis.spark.io.postgres.PostgresAttributeStore
import geotrellis.spark.{io => _, _}
import geotrellis.spark.io._
import geotrellis.spark.io.s3.S3ValueReader
import geotrellis.vector.{Extent, Projected}

import io.circe._
import io.circe.parser._
import io.circe.generic.semiauto._

import spray.json._
import DefaultJsonProtocol._

import java.net.URI
import java.util.UUID

case class ProjectNode(
    projectId: UUID,
    redBandOverride: Option[Int] = None,
    greenBandOverride: Option[Int] = None,
    blueBandOverride: Option[Int] = None,
    isSingleBand: Boolean = false,
    singleBandOptions: Option[SingleBandOptions.Params] = None
) {
  def getBandOverrides: Option[(Int, Int, Int)] =
    (redBandOverride, greenBandOverride, blueBandOverride).tupled
}

object ProjectNode extends RollbarNotifier with HistogramJsonFormats {

  // imported here so import ...backsplash.nodes._ doesn't import a transactor
  import com.azavea.rf.database.util.RFTransactor.xa

  val store = PostgresAttributeStore()

  implicit val projectNodeDecoder = deriveDecoder[ProjectNode]
  implicit val projectNodeEncoder = deriveEncoder[ProjectNode]

  implicit val projectNodeTmsReification: MamlTmsReification[ProjectNode] =
    new MamlTmsReification[ProjectNode] {
      def kind(self: ProjectNode): MamlKind = MamlKind.Tile

      def tmsReification(self: ProjectNode, buffer: Int)(
          implicit t: Timer[IO]): (Int, Int, Int) => IO[Literal] =
        (z: Int, x: Int, y: Int) => {
          val extent = CogUtils.tmsLevels(z).mapTransform.keyToExtent(x, y)
          val mdIO = self.getBandOverrides match {
            case Some((red, green, blue)) =>
              SceneToProjectDao
                .getMosaicDefinition(
                  self.projectId,
                  Some(Projected(extent, 3857)),
                  Some(red),
                  Some(green),
                  Some(blue)
                )
                .transact(xa)
            case None =>
              SceneToProjectDao
                .getMosaicDefinition(
                  self.projectId,
                  Some(Projected(extent, 3857))
                )
                .transact(xa)
          }
          for {
            mds <- mdIO
            mbTiles <- mds.toList.traverse(self.isSingleBand match {
              case false =>
                getMultiBandTileFromMosaic(z, x, y, extent)
              case true =>
                getSingleBandTileFromMosaic(
                  z,
                  x,
                  y,
                  extent,
                  self.singleBandOptions getOrElse {
                    throw SingleBandOptionsError(
                      "No single-band options found for single-band visualization")
                  })
            })
          } yield {
            RasterLit(
              mbTiles.flatten match {
                case Nil              => Raster(IntArrayTile.fill(NODATA, 256, 256), extent)
                case tiles @ (h :: _) => tiles reduce { _ merge _ }
              }
            )
          }
        }
    }

  def getSingleBandTileFromMosaic(z: Int,
                                  x: Int,
                                  y: Int,
                                  extent: Extent,
                                  singleBandOptions: SingleBandOptions.Params)(
      md: MosaicDefinition)(implicit t: Timer[IO]): IO[Option[Raster[Tile]]] =
    md.sceneType match {
      case Some(SceneType.COG) =>
        fetchSingleBandCogTile(md,
                                              z,
                                              x,
                                              y,
                                              extent,
                                              singleBandOptions).value
      case Some(SceneType.Avro) =>
        fetchSingleBandAvroTile(md,
                                               z,
                                               x,
                                               y,
                                               extent,
                                               singleBandOptions).value
      case None =>
        throw UnknownSceneType("Unable to fetch tiles with unknown scene type")
    }

  def getMultiBandTileFromMosaic(z: Int, x: Int, y: Int, extent: Extent)(
      md: MosaicDefinition)(implicit t: Timer[IO]): IO[Option[Raster[Tile]]] =
    md.sceneType match {
      case Some(SceneType.COG) =>
        fetchMultiBandCogTile(md, z, x, y, extent).value
      case Some(SceneType.Avro) =>
        fetchMultiBandAvroTile(md, z, x, y, extent).value
      case None =>
        throw UnknownSceneType("Unable to fetch tiles with unknown scene type")
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

  def colorSingleBandTile(
      tile: Tile,
      extent: Extent,
      histogram: Histogram[Double],
      singleBandOptions: SingleBandOptions.Params): Raster[Tile] = {
    val colorScheme = singleBandOptions.colorScheme
    val colorMap = (colorScheme.asArray,
                    colorScheme.asObject,
                    singleBandOptions.extraNoData) match {
      case (Some(a), None, _) =>
        ColorRampMosaic.colorMapFromVector(a.map(_.noSpaces),
                                           singleBandOptions,
                                           histogram)
      case (None, Some(o), Nil) =>
        ColorRampMosaic.colorMapFromMap(o.toMap map {
          case (k, v) => (k, v.noSpaces)
        })
      case (None, Some(o), masked @ (h +: t)) =>
        ColorRampMosaic.colorMapFromMap(o.toMap map {
          case (k, v) =>
            (k, if (masked.contains(k.toInt)) "#00000000" else v.noSpaces)
        })
      case _ =>
        val message =
          "Invalid color scheme format. Color schemes must be defined as an array of hex colors or a mapping of raster values to hex colors."
        throw SingleBandOptionsError(message)
    }
    Raster(tile.color(colorMap), extent)
  }

  def getCroppedGridBounds(tile: MultibandTile,
                           zoom: Int,
                           col: Int,
                           row: Int,
                           sourceZoom: Int): GridBounds = {
    val resolutionDiff = 1 << (zoom - sourceZoom)
    val innerCol = col % resolutionDiff
    val innerRow = row % resolutionDiff
    val cols = tile.cols / resolutionDiff
    val rows = tile.rows / resolutionDiff
    GridBounds(
      colMin = innerCol * cols,
      rowMin = innerRow * rows,
      colMax = (innerCol + 1) * cols - 1,
      rowMax = (innerRow + 1) * rows - 1
    )
  }

  // TODO: this essentially inlines a bunch of logic from LayerCache, which isn't super cool
  // it would be nice to get that logic somewhere more appropriate, especially since a lot of
  // it is grid <-> geometry math, but I'm not certain where it should go.
  def fetchMultiBandAvroTile(
      md: MosaicDefinition,
      zoom: Int,
      col: Int,
      row: Int,
      extent: Extent)(implicit t: Timer[IO]): OptionT[IO, Raster[Tile]] = {
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
              val corrected = if (zoom > sourceZoom) {
                md.colorCorrections.colorCorrect(
                  mbTile.crop(
                    getCroppedGridBounds(mbTile, zoom, col, row, sourceZoom)
                  ),
                  histograms.toSeq)
              } else {
                md.colorCorrections.colorCorrect(mbTile, histograms.toSeq)
              }
              Raster(corrected.color, extent).resample(256, 256)
            }
        }).toOption
      }
    )
  }

  def fetchMultiBandCogTile(
      md: MosaicDefinition,
      zoom: Int,
      col: Int,
      row: Int,
      extent: Extent)(implicit t: Timer[IO]): OptionT[IO, Raster[Tile]] = {
    val tileIO = for {
      _ <- IO.pure(
        logger.debug(
          s"Fetching multi-band COG tile for scene ID ${md.sceneId}"))
      raster <- CogUtils.fetch(
        md.ingestLocation.getOrElse(
          throw UningestedScenes(s"Scene ${md.sceneId} is not yet ingested")),
        zoom,
        col,
        row)
      histograms <- layerHistogram(md.sceneId)
    } yield {
      val bandOrder = List(
        md.colorCorrections.redBand,
        md.colorCorrections.greenBand,
        md.colorCorrections.blueBand
      )
      val subsetBands = raster.tile.subsetBands(bandOrder)
      val subsetHistograms = bandOrder map histograms
      val normalized = (
        subsetBands.mapBands { (i: Int, tile: Tile) =>
          {
            (subsetHistograms(i).minValue, subsetHistograms(i).maxValue) match {
              case (Some(min), Some(max)) => tile.normalize(min, max, 0, 255)
              case (min, max) =>
                throw MetadataError(
                  s"Histogram for ${md.sceneId} has invalid min or max values. Min: $min, Max: $max")
            }
          }
        }
      ).color

      Raster(normalized, extent).resample(256, 256)
    }
    OptionT(tileIO.attempt.map(_.toOption))
  }

  def fetchSingleBandAvroTile(md: MosaicDefinition,
                              zoom: Int,
                              col: Int,
                              row: Int,
                              extent: Extent,
                              singleBandOptions: SingleBandOptions.Params)(
      implicit t: Timer[IO]): OptionT[IO, Raster[Tile]] = {
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
                throw SingleBandOptionsError(
                  s"Band ${singleBandOptions.band} not present in data for ${md.sceneId}")
              }
              val histogram = histograms
                .lift(singleBandOptions.band) getOrElse {
                throw MetadataError(
                  s"No histogram found for band ${singleBandOptions.band} in ${md.sceneId}")
              }

              if (zoom > sourceZoom) {
                colorSingleBandTile(
                  tile.crop(
                    getCroppedGridBounds(mbTile, zoom, col, row, sourceZoom)
                  ),
                  extent,
                  histogram,
                  singleBandOptions
                )
              } else {
                colorSingleBandTile(tile, extent, histogram, singleBandOptions)
              }

            }
        }).toOption
      }
    )
  }

  def fetchSingleBandCogTile(md: MosaicDefinition,
                             zoom: Int,
                             col: Int,
                             row: Int,
                             extent: Extent,
                             singleBandOptions: SingleBandOptions.Params)(
      implicit t: Timer[IO]): OptionT[IO, Raster[Tile]] = {
    val tileIO = for {
      _ <- IO.pure(
        logger.debug(
          s"Fetching single-band COG tile for scene ID ${md.sceneId}"))
      raster <- CogUtils.fetch(
        md.ingestLocation.getOrElse(
          throw UningestedScenes(s"Scene ${md.sceneId} is not yet ingested")),
        zoom,
        col,
        row)
      histograms <- layerHistogram(md.sceneId)
    } yield {
      val tile = raster.tile.bands.lift(singleBandOptions.band) getOrElse {
        throw SingleBandOptionsError("No band found in single-band options")
      }
      val histogram = histograms
        .lift(singleBandOptions.band) getOrElse {
        throw MetadataError(
          s"No histogram found for band ${singleBandOptions.band} in ${md.sceneId}")
      }
      colorSingleBandTile(tile, extent, histogram, singleBandOptions)
    }
    OptionT(tileIO.attempt.map(_.toOption))
  }
}
