package com.rasterfoundry.backsplash.nodes

import com.rasterfoundry.database.util.RFTransactor

import cats.data.{NonEmptyList => NEL}
import cats.effect._
import cats.implicits._
import com.azavea.maml.ast.{Literal, MamlKind, RasterLit}
import com.rasterfoundry.backsplash.io.Mosaic
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.datamodel._
import com.rasterfoundry.tool.ast.MapAlgebraAST
import com.rasterfoundry.database.ToolRunDao

import geotrellis.raster.io.json.HistogramJsonFormats
import geotrellis.raster.{Raster, io => _, _}
import geotrellis.server._
import geotrellis.server.cog.util.CogUtils
import geotrellis.spark.io.postgres.PostgresAttributeStore
import geotrellis.spark.tiling.LayoutDefinition
import geotrellis.spark.{io => _}
import geotrellis.server.{TmsReification, ExtentReification}
import geotrellis.proj4.CRS

import io.circe.generic.semiauto._
import io.circe.Json
import geotrellis.vector._

import java.util.UUID

case class LabNode(
    projectId: UUID,
    band: Int
) {
  def toProjectNode: ProjectNode =
    ProjectNode.apply(
      projectId,
      None,
      None,
      None,
      true,
      // BS options to fulfill project node requirements
      Some(
        SingleBandOptions
          .Params(band, BandDataType.Diverging, 0, Json.Null, "Up")),
      true
    )
}

object LabNode extends RollbarNotifier with HistogramJsonFormats {

  // imported here so import ...backsplash.nodes._ doesn'contextShift import a transactor
  import com.rasterfoundry.database.util.RFTransactor.xa
  implicit val xa = RFTransactor.xa

  val store = PostgresAttributeStore()

  implicit val labNodeDecoder = deriveDecoder[LabNode]
  implicit val labNodeEncoder = deriveEncoder[LabNode]

  def getClosest(cellWidth: Double, listNums: List[LayoutDefinition]) =
    listNums match {
      case Nil  => Double.MaxValue
      case list => list.minBy(ld => math.abs(ld.cellSize.width - cellWidth))
    }

  implicit val labNodeTmsReification: TmsReification[LabNode] =
    new TmsReification[LabNode] {
      def kind(self: LabNode): MamlKind = MamlKind.Tile

      def tmsReification(self: LabNode, buffer: Int)(
          implicit contextShift: ContextShift[IO])
        : (Int, Int, Int) => IO[Literal] =
        (z: Int, x: Int, y: Int) => {
          val extent = CogUtils.tmsLevels(z).mapTransform.keyToExtent(x, y)
          val mdIO =
            Mosaic.getMosaicDefinitions(self.toProjectNode, Some(extent))
          for {
            mds <- mdIO
            mbTiles <- Mosaic.getMosaicDefinitionTiles(self.toProjectNode,
                                                       z,
                                                       x,
                                                       y,
                                                       extent,
                                                       mds)
          } yield {
            RasterLit(
              mbTiles.flatten match {
                case Nil => {
                  logger.info(s"tile with NO DATA")
                  Raster(IntArrayTile.fill(NODATA, 256, 256), extent)
                }
                case tiles @ (h :: _) =>
                  tiles reduce {
                    _ merge _
                  }
              }
            )
          }
        }
    }

  implicit val labNodeExtentReification: ExtentReification[LabNode] =
    new ExtentReification[LabNode] {
      def kind(self: LabNode): MamlKind = MamlKind.Tile
      // labnode should have band number on it or a way of retrieving it
      def extentReification(self: LabNode)(
          implicit contextShift: ContextShift[IO])
        : (Extent, CellSize) => IO[Literal] =
        (extent: Extent, cs: CellSize) => {
          logger.info(
            s"In ExtentReification extentReification with extent ${extent}")
          for {
            mds <- Mosaic.getMosaicDefinitions(self.toProjectNode, None)
            // should always be one for a node
            tiffs <- mds.toList
              .map(_.ingestLocation)
              .flatten
              .traverse {
                CogUtils
                  .fromUri(_)
                  .map(CogUtils.cropGeoTiffToTile(_, extent, cs, self.band))
              }
          } yield {
            RasterLit(
              tiffs match {
                case Nil => {
                  Raster(IntArrayTile.fill(NODATA, 256, 256), extent)
                }
                case tiles @ (h :: _) =>
                  Raster(tiles reduce {
                    _ merge _
                  }, extent)
              }
            )
          }
        }
    }

  implicit val labNodeHasRasterExtents: HasRasterExtents[LabNode] =
    new HasRasterExtents[LabNode] {
      def rasterExtents(self: LabNode)(
          implicit contextShift: ContextShift[IO]): IO[NEL[RasterExtent]] = {
        val mdIO = Mosaic.getMosaicDefinitions(self.toProjectNode)
        for {
          mds <- mdIO
          tiff <- CogUtils.fromUri(mds.head.ingestLocation.get)
        } yield {
          NEL(tiff.rasterExtent, tiff.overviews.map(_.rasterExtent))
        }
      }

      def crs(self: LabNode)(
          implicit contextShift: ContextShift[IO]): IO[CRS] = {
        val mdIO = Mosaic.getMosaicDefinitions(self.toProjectNode)
        for {
          mds <- mdIO
          tiff <- CogUtils.fromUri(mds.head.ingestLocation.get)
        } yield {
          tiff.crs
        }
      }
    }
}
