package com.rasterfoundry.backsplash.nodes

import com.rasterfoundry.database.util.RFTransactor

import cats.data.{NonEmptyList => NEL}
import cats.effect._
import cats.implicits._
import com.azavea.maml.ast.{Literal, MamlKind, RasterLit}
import com.rasterfoundry.backsplash.io.Mosaic
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.datamodel.SingleBandOptions
import fs2.Stream
import geotrellis.proj4.{io => _, _}
import geotrellis.raster.io.json.HistogramJsonFormats
import geotrellis.raster.{Raster, io => _, _}
import geotrellis.server._
import geotrellis.server.cog.util.CogUtils
import geotrellis.spark.io.postgres.PostgresAttributeStore
import geotrellis.spark.tiling.LayoutDefinition
import geotrellis.vector.Extent
import io.circe.generic.semiauto._

import java.util.UUID

final case class ProjectNode(
    projectId: UUID,
    redBandOverride: Option[Int] = None,
    greenBandOverride: Option[Int] = None,
    blueBandOverride: Option[Int] = None,
    isSingleBand: Boolean = false,
    singleBandOptions: Option[SingleBandOptions.Params] = None,
    rawSingleBandValues: Boolean = true
) {
  def getBandOverrides: Option[(Int, Int, Int)] =
    (redBandOverride, greenBandOverride, blueBandOverride).tupled
}

object ProjectNode extends RollbarNotifier with HistogramJsonFormats {

  implicit val xa = RFTransactor.xa

  val store = PostgresAttributeStore()

  implicit val projectNodeDecoder = deriveDecoder[ProjectNode]
  implicit val projectNodeEncoder = deriveEncoder[ProjectNode]

  implicit val projectNodeTmsReification: TmsReification[ProjectNode] =
    new TmsReification[ProjectNode] {
      def kind(self: ProjectNode): MamlKind = MamlKind.Tile

      def tmsReification(self: ProjectNode, buffer: Int)(
          implicit contextShift: ContextShift[IO])
        : (Int, Int, Int) => IO[Literal] =
        (z: Int, x: Int, y: Int) => {
          val extent = CogUtils.tmsLevels(z).mapTransform.keyToExtent(x, y)
          (for {
            md <- Mosaic.getMosaicDefinitions(self, extent)
            mbTile <- Stream.eval {
              Mosaic.getMosaicDefinitionTile(self, z, x, y, extent, md)
            }
          } yield { mbTile })
            .collect({ case Some(i) => i })
            .compile
            .fold(IntArrayTile.fill(NODATA, 256, 256): Tile)(
              (t1: Tile, raster: Raster[Tile]) => {
                t1 merge raster.tile
              }
            ) map { (tile: Tile) =>
            RasterLit(Raster(tile, extent))
          }
        }
    }

  implicit val extentReification: ExtentReification[ProjectNode] =
    new ExtentReification[ProjectNode] {
      def kind(self: ProjectNode): MamlKind = MamlKind.Tile

      def extentReification(self: ProjectNode)(
          implicit contextShift: ContextShift[IO]) =
        (extent: Extent, cellSize: CellSize) => {
          (for {
            md <- Mosaic.getMosaicDefinitions(self,
                                              extent.reproject(LatLng,
                                                               WebMercator))
            correctedMd = if (!self.isSingleBand) {
              (self.redBandOverride,
               self.greenBandOverride,
               self.blueBandOverride).tupled match {
                case Some((r, g, b)) =>
                  md.copy(
                    colorCorrections = md.colorCorrections.copy(
                      redBand = r,
                      greenBand = g,
                      blueBand = b
                    )
                  )
                case _ => md
              }
            } else md
            mbTile <- Stream.eval {
              Mosaic.getMosaicTileForExtent(extent,
                                            cellSize,
                                            self.singleBandOptions,
                                            self.isSingleBand)(correctedMd)
            }
          } yield { mbTile })
            .collect({ case Some(i) => i })
            .compile
            .fold(IntArrayTile.fill(NODATA, 256, 256): Tile)(
              (t1: Tile, raster: Raster[Tile]) => t1 merge raster.tile
            ) map { (tile: Tile) =>
            RasterLit(Raster(tile, extent))
          }
        }
    }
}
