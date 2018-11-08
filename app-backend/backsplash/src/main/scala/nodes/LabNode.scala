package com.rasterfoundry.backsplash.nodes

import com.rasterfoundry.database.util.RFTransactor

import cats.data.{NonEmptyList => NEL}
import cats.effect._
import cats.implicits._
import com.azavea.maml.ast.{Literal, MamlKind, RasterLit}
import com.rasterfoundry.backsplash.io.{Mosaic, Avro}
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.datamodel._
import com.rasterfoundry.tool.ast.MapAlgebraAST
import com.rasterfoundry.database.{ToolRunDao, LayerAttributeDao}
import com.rasterfoundry.backsplash.error._

import doobie.implicits._
import scala.util._

import geotrellis.raster.io.json.HistogramJsonFormats
import geotrellis.raster.{Raster, io => _, _}
import geotrellis.server._
import geotrellis.server.cog.util.CogUtils
import geotrellis.spark.io.postgres.PostgresAttributeStore
import geotrellis.spark.io.s3.S3CollectionLayerReader
import geotrellis.spark.tiling.LayoutDefinition
import geotrellis.spark.{SpatialKey, TileLayerMetadata, io => _, _}
import geotrellis.server.{TmsReification, ExtentReification}
import geotrellis.proj4.CRS

import io.circe.generic.semiauto._
import io.circe.Json
import geotrellis.vector._
import spray.json.DefaultJsonProtocol._
import spray.json._
import geotrellis.spark.io._

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
      def getMosaicDefinitionTiles(md: MosaicDefinition,
                                   extent: Extent,
                                   cs: CellSize,
                                   band: Int): IO[Tile] = {
        (md.sceneType, md.ingestLocation) match {
          case (Some(SceneType.COG), Some(ingestLocation)) =>
            CogUtils
              .fromUri(ingestLocation) // OptionT[Future, TiffWithMetadata]
              .map(CogUtils.cropGeoTiffToTile(_, extent, cs, band)) // OptionT[Future, Tile]
          case (Some(SceneType.Avro), _) =>
            IO {
              Avro
                .minZoomLevel(store, md.sceneId.toString, cs.resolution.toInt)
                .map {
                  case (layerId, re) =>
                    S3CollectionLayerReader(store)
                      .query[SpatialKey,
                             MultibandTile,
                             TileLayerMetadata[SpatialKey]](layerId)
                      .where(Intersects(re.extent))
                      .result
                      .stitch
                      .crop(re.extent)
                } match {
                case Success(raster) =>
                  raster.tile.band(band)
                case Failure(e) => throw e
              }
            }
          case _ =>
            throw UnknownSceneTypeException(
              s"Scene with id: ${md.sceneId} has an unallowed scene type: ${md.sceneType}")
        }
      }
      def kind(self: LabNode): MamlKind = MamlKind.Tile

      def extentReification(self: LabNode)(
          implicit contextShift: ContextShift[IO])
        : (Extent, CellSize) => IO[Literal] =
        (extent: Extent, cs: CellSize) => {
          logger.info(
            s"In ExtentReification extentReification with extent ${extent}")
          for {
            mds <- Mosaic.getMosaicDefinitions(self.toProjectNode, None)
            tiffs <- mds.toList
              .filter(_.ingestLocation != None)
              .traverse(getMosaicDefinitionTiles(_, extent, cs, self.band))
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
        logger.info("In rasterExtents")
        for {
          mds <- Mosaic.getMosaicDefinitions(self.toProjectNode)
          extents <- mds.toList
            .filter(_.ingestLocation != None)
            .toNel match {
            case Some(nel) =>
              nel.traverse(
                md => {
                  (md.sceneType, md.ingestLocation) match {
                    case (Some(SceneType.COG), Some(ingestLocation)) =>
                      CogUtils
                        .fromUri(ingestLocation)
                        .map(t =>
                          NEL(t.rasterExtent, t.overviews.map(_.rasterExtent)))
                    case (Some(SceneType.Avro), _) =>
                      // Avro tile function
                      LayerAttributeDao
                        .unsafeGetAttribute(LayerId(md.sceneId.toString, 0),
                                            "extent")
                        .transact(xa)
                        .map { la =>
                          val bbox = la.value.noSpaces
                          NEL(RasterExtent(
                                Extent.fromString(
                                  bbox.substring(1, bbox.length() - 1)),
                                // TODO: This needs needs to be done more intelligently? It's causing massive issues
                                // Noted issues:
                                // Tiles are timing out, and never releasing the hikari connection. This causes
                                // connection starvation and breaks everything.
                                CellSize(30, 30)),
                              Nil)
                        }
                    case _ =>
                      throw UnknownSceneTypeException(
                        s"Scene with id: ${md.sceneId} has an unallowed scene type: ${md.sceneType}")
                  }
                }
              )
            case _ =>
              throw UningestedScenesException(
                "Projects must have at least one ingested scene to be rendered")
          }
        } yield extents.flatten
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
