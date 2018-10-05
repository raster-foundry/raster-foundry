package com.azavea.rf.backsplash.nodes

import java.util.UUID

import cats.data.{NonEmptyList => NEL}
import cats.effect.{IO, Timer}
import cats.implicits._
import com.azavea.maml.ast.{Literal, MamlKind, RasterLit}
import com.azavea.rf.backsplash.io.Mosaic
import com.azavea.rf.common.RollbarNotifier
import com.azavea.rf.datamodel.SingleBandOptions
import geotrellis.raster.io.json.HistogramJsonFormats
import geotrellis.raster.{Raster, io => _, _}
import geotrellis.server.core.cog.CogUtils
import geotrellis.server.core.maml.reification._
import geotrellis.spark.io.postgres.PostgresAttributeStore
import geotrellis.spark.tiling.LayoutDefinition
import geotrellis.spark.{io => _}
import io.circe.generic.semiauto._

case class ProjectNode(
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

  // imported here so import ...backsplash.nodes._ doesn't import a transactor
  import com.azavea.rf.database.util.RFTransactor.xa

  val store = PostgresAttributeStore()

  implicit val projectNodeDecoder = deriveDecoder[ProjectNode]
  implicit val projectNodeEncoder = deriveEncoder[ProjectNode]

  def getClosest(cellWidth: Double, listNums: List[LayoutDefinition]) =
    listNums match {
      case Nil  => Double.MaxValue
      case list => list.minBy(ld => math.abs(ld.cellSize.width - cellWidth))
    }

  implicit val projectNodeTmsReification: MamlTmsReification[ProjectNode] =
    new MamlTmsReification[ProjectNode] {
      def kind(self: ProjectNode): MamlKind = MamlKind.Tile

      def tmsReification(self: ProjectNode, buffer: Int)(
          implicit t: Timer[IO]): (Int, Int, Int) => IO[Literal] =
        (z: Int, x: Int, y: Int) => {
          val extent = CogUtils.tmsLevels(z).mapTransform.keyToExtent(x, y)
          val mdIO = Mosaic.getMosaicDefinitions(self, extent)
          for {
            mds <- mdIO
            mbTiles <- Mosaic.getMosaicDefinitionTiles(self,
                                                       z,
                                                       x,
                                                       y,
                                                       extent,
                                                       mds)
          } yield {
            RasterLit(
              mbTiles.flatten match {
                case Nil => {
                  logger.info(s"NO DATA")
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
}
