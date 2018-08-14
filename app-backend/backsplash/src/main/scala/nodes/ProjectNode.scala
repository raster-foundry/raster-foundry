package com.azavea.rf.backsplash.nodes

import com.azavea.maml.ast.{Literal, MamlKind, RasterLit}
import com.azavea.rf.database.SceneToProjectDao
import com.azavea.rf.datamodel.{MosaicDefinition, SceneType}

import cats.effect.{IO, Timer}
import cats.implicits._
import doobie.implicits._
import geotrellis.raster.{CellSize, CellType, Raster}
import geotrellis.server.core.cog.CogUtils
import geotrellis.server.core.maml.CogNode
import geotrellis.server.core.maml.persistence._
import geotrellis.server.core.maml.metadata._
import geotrellis.server.core.maml.reification._
import geotrellis.raster.{Raster, MultibandRaster, Tile}
import geotrellis.spark.io.postgres.PostgresAttributeStore
import geotrellis.vector.{Extent, Projected}

import io.circe._
import io.circe.generic.semiauto._

import java.net.URI
import java.util.UUID

case class ProjectNode(
  projectId: UUID
)

object ProjectNode {

  // imported here so import ...backsplash.nodes._ doesn't import a transactor
  import com.azavea.rf.database.util.RFTransactor.xa

  val store = PostgresAttributeStore()

  implicit val projectNodeDecoder = deriveDecoder[ProjectNode]
  implicit val projectNodeEncoder = deriveEncoder[ProjectNode]

  implicit val projectNodeTmsReification: MamlTmsReification[ProjectNode] =
    new MamlTmsReification[ProjectNode] {
      def kind(self: ProjectNode): MamlKind = MamlKind.Tile

      def fetchAvroTile(md: MosaicDefinition, zoom: Int, col: Int, row: Int): IO[Raster[Tile]] = {
        ???
      }

      def fetchCogTile(md: MosaicDefinition, extent: Extent): IO[Raster[Tile]] =
        for {
          rasterTile <- CogUtils.fetch(
            md.ingestLocation.getOrElse("Cannot fetch scene with no ingest location"), extent
          )
        } yield {
          val subset = rasterTile.tile.subsetBands(
            md.colorCorrections.redBand,
            md.colorCorrections.greenBand,
            md.colorCorrections.blueBand
          )
          val hist = subset.histogram
          val normalized = (subset.mapBands {
            (i: Int, tile: Tile) => tile.rescale(0, 255)
          }).color
          println(normalized.toArray.take(10).toList)
          Raster(
            normalized,
            extent
          )
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
                case md@MosaicDefinition(_, _, Some(SceneType.COG), _) => fetchCogTile(md, extent)
                case md@MosaicDefinition(_, _, Some(SceneType.Avro), _) => fetchAvroTile(md, z, x, y)
                case _ => throw new Exception("Unable to fetch tiles with unknown scene type")
              }
            )
          } yield {
            RasterLit(mbTiles reduce { _ merge _ })
          }
        }
    }
}
