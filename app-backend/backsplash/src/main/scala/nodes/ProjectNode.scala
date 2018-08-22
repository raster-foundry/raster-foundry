package com.azavea.rf.backsplash.nodes

import com.azavea.maml.ast.{Literal, MamlKind, RasterLit}
import com.azavea.rf.common.RollbarNotifier
import com.azavea.rf.database._
import com.azavea.rf.datamodel.{HistogramAttribute, LayerAttribute, MosaicDefinition, SceneType}

import cats.data.{OptionT, EitherT}
import cats.effect.{IO, Timer}
import cats.implicits._
import doobie.implicits._
import geotrellis.raster.{CellSize, CellType, Raster}
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
  projectId: UUID
)

object ProjectNode extends RollbarNotifier with HistogramJsonFormats {

  // imported here so import ...backsplash.nodes._ doesn't import a transactor
  import com.azavea.rf.database.util.RFTransactor.xa

  val store = PostgresAttributeStore()

  implicit val projectNodeDecoder = deriveDecoder[ProjectNode]
  implicit val projectNodeEncoder = deriveEncoder[ProjectNode]

  implicit val projectNodeTmsReification: MamlTmsReification[ProjectNode] =
    new MamlTmsReification[ProjectNode] {
      def kind(self: ProjectNode): MamlKind = MamlKind.Tile

      def tmsReification(self: ProjectNode, buffer: Int)(implicit t: Timer[IO]): (Int, Int, Int) => IO[Literal] =
        (z: Int, x: Int, y: Int) => {
          val extent = CogUtils.tmsLevels(z).mapTransform.keyToExtent(x, y)
          val mdIO = SceneToProjectDao.getMosaicDefinition(
            self.projectId, Some(Projected(extent, 3857))
          ).transact(xa)
          for {
            mds <- mdIO
            mbTiles <- mds.toList.parTraverse(
              {
                case md@MosaicDefinition(_, _, Some(SceneType.COG), _) =>
                  IO.shift(t) *> fetchCogTile(md, extent).value
                case md@MosaicDefinition(_, _, Some(SceneType.Avro), _) =>
                  IO.shift(t) *> fetchAvroTile(md, z, x, y).value
                case MosaicDefinition(_, _, None, _) =>
                  throw new Exception("Unable to fetch tiles with unknown scene type")
              }
            )
          } yield {
            RasterLit(
              mbTiles.flatten match {
                case Nil => Raster(IntArrayTile.fill(NODATA, 256, 256), extent)
                case tiles@(h :: _) => tiles reduce { _ merge _ }
              }
            )
          }
        }
    }

  def tileLayerMetadata(id: UUID, zoom: Int): IO[(Int, TileLayerMetadata[SpatialKey])] = {

    logger.debug(s"Requesting tile layer metadata (layer: $id, zoom: $zoom")
    val layerName = id.toString
    LayerAttributeDao.unsafeMaxZoomForLayer(layerName).transact(xa) map {
      case (_, maxZoom) =>
        val z = if (zoom > maxZoom) maxZoom else zoom
        z -> store.readMetadata[TileLayerMetadata[SpatialKey]](LayerId(layerName, z))
    }
  }

  def avroLayerHistogram(id: UUID): IO[Array[Histogram[Double]]] = {
    logger.debug(s"Fetching histogram for scene id $id")
    val layerId = LayerId(name = id.toString, zoom = 0)
    LayerAttributeDao.unsafeGetAttribute(layerId, "histogram").transact(xa) map {
      attribute => attribute.value.noSpaces.parseJson.convertTo[Array[Histogram[Double]]]
    }
  }

  // TODO: don't do this. get COG histograms into the attribute store on import then just use
  // layerHistogram
  def cogLayerMinMax(uri: String): IO[(Int, Int)] = {
    logger.debug(s"Fetching histogram for cog at $uri")
    for {
      geotiff <- CogUtils.getTiff(uri)
    } yield {
      val cellType = geotiff.cellType
      val signed = cellType.toString.startsWith("u")
      val max = if (signed) 1 << cellType.bits / 2 else 1 << cellType.bits
      val min = if (signed) -max else 0
      logger.debug(s"Fetched min and max for cog at $uri: $max, $min")
      (min, max)
    }
  }

  def avroLayerTile(id: UUID, zoom: Int, key: SpatialKey): IO[MultibandTile] = {
    val reader = new S3ValueReader(store).reader[SpatialKey, MultibandTile](LayerId(id.toString, zoom))
    IO(reader.read(key))
  }

  // TODO: this essentially inlines a bunch of logic from LayerCache, which isn't super cool
  // it would be nice to get that logic somewhere more appropriate, especially since a lot of
  // it is grid <-> geometry math, but I'm not certain where it should go.
  def fetchAvroTile(md: MosaicDefinition, zoom: Int, col: Int, row: Int)(implicit t: Timer[IO]): OptionT[IO, Raster[Tile]] = {
    logger.debug(s"Fetching avro tile for scene id ${md.sceneId}")
    OptionT(
      for {
        metadata <- IO.shift(t) *> tileLayerMetadata(md.sceneId, zoom)
                                                    (sourceZoom, tlm) = metadata
        zoomDiff = zoom - sourceZoom
        resolutionDiff = 1 << zoomDiff
        sourceKey = SpatialKey(col / resolutionDiff, row / resolutionDiff)
        histograms <- IO.shift(t) *> avroLayerHistogram(md.sceneId)
        mbTileE <- {
          if (tlm.bounds.includes(sourceKey))
            avroLayerTile(md.sceneId, sourceZoom, sourceKey).attempt
          else IO(
            Left(
              new Exception(s"Source key outside of tile layer bounds for scene ${md.sceneId}, key ${sourceKey}")
            )
          )
        }
      } yield {
        val coloredTileE = mbTileE map {
          (mbTile: MultibandTile) => {
            val extent = CogUtils.tmsLevels(zoom).mapTransform.keyToExtent(col, row)
            val innerCol = col % resolutionDiff
            val innerRow = row % resolutionDiff
            val cols = mbTile.cols / resolutionDiff
            val rows = mbTile.rows / resolutionDiff
            val corrected = md.colorCorrections.colorCorrect(mbTile, histograms.toSeq)
            Raster(corrected.color, extent).resample(256, 256)
          }
        }
        coloredTileE.toOption
      }
    )
  }

  def fetchCogTile(md: MosaicDefinition, extent: Extent)(implicit t: Timer[IO]): OptionT[IO, Raster[Tile]] = {
    logger.debug(s"Fetching COG tile for scene ID ${md.sceneId}")
    val tileIO = for {
      rasterTile <- IO.shift(t) *> CogUtils.fetch(md.ingestLocation.getOrElse("Cannot fetch scene with no ingest location"), extent
      )
      histograms <- IO.shift(t) *> cogLayerMinMax(md.ingestLocation.getOrElse(""))
    } yield {
      val bandOrder = List(
        md.colorCorrections.redBand,
        md.colorCorrections.greenBand,
        md.colorCorrections.blueBand
      )
      val subset = rasterTile.tile.subsetBands(bandOrder: _*)
      val normalized = (
        subset.mapBands {
          (i: Int, tile: Tile) => {
            val (minValue, maxValue) = histograms
            tile.normalize(minValue, maxValue, 0, 255)
          }
        }
      ).color
      Raster(normalized, extent).resample(256, 256)
    }
    OptionT(tileIO.attempt.map(_.toOption))
  }

}
