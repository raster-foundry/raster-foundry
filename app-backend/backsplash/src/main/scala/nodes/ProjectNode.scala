package com.azavea.rf.backsplash.nodes

import com.azavea.maml.ast.{Literal, MamlKind, RasterLit}
import com.azavea.rf.common.RollbarNotifier
import com.azavea.rf.database._
import com.azavea.rf.datamodel.{MosaicDefinition, SceneType}

import cats.data.OptionT
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
import geotrellis.spark.io.postgres.PostgresAttributeStore
import geotrellis.spark.{io => _, _}
import geotrellis.spark.io._
import geotrellis.spark.io.s3.S3ValueReader
import geotrellis.vector.{Extent, Projected}

import io.circe._
import io.circe.generic.semiauto._

import java.net.URI
import java.util.UUID

case class ProjectNode(
  projectId: UUID
)

object ProjectNode extends RollbarNotifier {

  // imported here so import ...backsplash.nodes._ doesn't import a transactor
  import com.azavea.rf.database.util.RFTransactor.xa

  val store = PostgresAttributeStore()

  implicit val projectNodeDecoder = deriveDecoder[ProjectNode]
  implicit val projectNodeEncoder = deriveEncoder[ProjectNode]

  implicit val projectNodeTmsReification: MamlTmsReification[ProjectNode] =
    new MamlTmsReification[ProjectNode] {
      def kind(self: ProjectNode): MamlKind = MamlKind.Tile

      def tileLayerMetadata(id: UUID, zoom: Int): IO[(Int, TileLayerMetadata[SpatialKey])] = {

        logger.debug(s"Requesting tile layer metadata (layer: $id, zoom: $zoom")
        val layerName = id.toString
        LayerAttributeDao.unsafeMaxZoomForLayer(layerName).transact(xa) map {
          case (_, maxZoom) =>
            val z = if (zoom > maxZoom) maxZoom else zoom
            z -> store.readMetadata[TileLayerMetadata[SpatialKey]](LayerId(layerName, z))
        }
      }

      def avroLayerTile(id: UUID, zoom: Int, key: SpatialKey): IO[MultibandTile] = {
        val reader = new S3ValueReader(store).reader[SpatialKey, MultibandTile](LayerId(id.toString, zoom))
        IO(reader.read(key))
      }

      def fetchAvroTile(md: MosaicDefinition, zoom: Int, col: Int, row: Int): OptionT[IO, Raster[Tile]] =
        OptionT(
          for {
            metadata <- tileLayerMetadata(md.sceneId, zoom)
            (sourceZoom, tlm) = metadata
            zoomDiff = zoom - sourceZoom
            resolutionDiff = 1 << zoomDiff
            sourceKey = SpatialKey(col / resolutionDiff, row / resolutionDiff)
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
                val subset = mbTile.crop(
                  GridBounds(
                    colMin = innerCol * cols,
                    rowMin = innerRow * rows,
                    colMax = (innerCol + 1) * cols - 1,
                    rowMax = (innerRow + 1) * rows - 1
                  )
                ).resample(256, 256).subsetBands(
                  md.colorCorrections.redBand,
                  md.colorCorrections.greenBand,
                  md.colorCorrections.blueBand
                )
                val normalized = subset.mapBands {
                  (i: Int, tile: Tile) => tile.rescale(0, 255)
                }
                Raster(normalized.color, extent).resample(256, 256)
              }
            }
            coloredTileE.toOption
          }
        )

      def fetchCogTile(md: MosaicDefinition, extent: Extent): OptionT[IO, Raster[Tile]] = {
        val tileIO = for {
          rasterTile <- CogUtils.fetch(md.ingestLocation.getOrElse("Cannot fetch scene with no ingest location"), extent
          )
        } yield {
          val subset = rasterTile.tile.subsetBands(
            md.colorCorrections.redBand,
            md.colorCorrections.greenBand,
            md.colorCorrections.blueBand
          )
          val normalized = (subset.mapBands {
                              (i: Int, tile: Tile) => tile.rescale(0, 255)
                            }).color
          Raster(
            normalized,
            extent
          ).resample(256, 256)
        }
        OptionT(tileIO.attempt.map(_.toOption)) 
      }

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
}
