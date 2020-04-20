package com.rasterfoundry.backsplash.export

import com.rasterfoundry.backsplash.ExportConfig

import cats.effect._
import cats.implicits._
import com.azavea.maml.ast._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.resample._
import geotrellis.server._
import geotrellis.layer._

object TileReification extends LazyLogging {

  private val tileSize = ExportConfig.Export.tileSize

  def getLayoutDefinition(zoom: Int): LayoutDefinition = {
    val webMercator = ZoomedLayoutScheme(WebMercator, 256)
    val cellSize = webMercator.levelForZoom(zoom).layout.cellSize
    FloatingLayoutScheme(tileSize)
      .levelFor(WebMercator.worldExtent, cellSize)
      .layout
  }

  def getNoDataValue(cellType: CellType): Option[Double] = {
    cellType match {
      case ByteUserDefinedNoDataCellType(value)   => Some(value.toDouble)
      case UByteUserDefinedNoDataCellType(value)  => Some(value.toDouble)
      case UByteConstantNoDataCellType            => Some(0)
      case ShortUserDefinedNoDataCellType(value)  => Some(value.toDouble)
      case UShortUserDefinedNoDataCellType(value) => Some(value.toDouble)
      case UShortConstantNoDataCellType           => Some(0)
      case IntUserDefinedNoDataCellType(value)    => Some(value.toDouble)
      case FloatUserDefinedNoDataCellType(value)  => Some(value.toDouble)
      case DoubleUserDefinedNoDataCellType(value) => Some(value.toDouble)
      case _: NoNoData                            => Some(Double.NaN)
      case _: ConstantNoData[_]                   => Some(Double.NaN)
    }
  }

  val invisiCellType: DoubleConstantNoDataCellType.type =
    DoubleConstantNoDataCellType
  val invisiTile: MutableArrayTile =
    ArrayTile.empty(invisiCellType, tileSize, tileSize)

  implicit val mosaicExportTmsReification =
    new TmsReification[List[(String, List[Int], Option[Double])]] {
      def kind(): MamlKind =
        MamlKind.Image

      def tmsReification(
          self: List[(String, List[Int], Option[Double])],
          buffer: Int
      )(
          implicit contextShift: ContextShift[IO]
      ) = (z: Int, x: Int, y: Int) => {
        val layoutDefinition = getLayoutDefinition(z)
        val extent = layoutDefinition.mapTransform.keyToExtent(x, y)
        logger.debug(s"Extent of Tile ($x, $y): ${extent}")
        val bandCount = {
          val first =
            self.headOption
              .getOrElse(
                throw new Exception(
                  "Tile location list must contain *some* tile locations"
                )
              )
          val explicitBandcount = first._2.length
          if (explicitBandcount < 1) {
            RasterSources.getOrUpdate(first._1).bandCount
          } else {
            explicitBandcount
          }
        }
        val subTilesIO: IO[List[Option[MultibandTile]]] =
          self.parTraverse {
            case (uri, bands, _) =>
              IO {
                val rs = RasterSources
                  .getOrUpdate(uri)
                  .reproject(WebMercator, method = NearestNeighbor)
                if (bands.length > rs.bandCount) {
                  val msg =
                    s"Number of bands requested (${bands.length}) is greater than bands in Raster Source (${rs.bandCount})"
                  throw new IllegalArgumentException(msg)
                }
                logger.debug(s"Raster Source Extent: ${rs.extent}")
                if (rs.extent.intersects(extent)) {
                  rs.tileToLayout(layoutDefinition, NearestNeighbor)
                    .read(SpatialKey(x, y), bands) match {
                    case Some(mbtile) => {
                      val noDataValue = getNoDataValue(mbtile.cellType)
                      Some(
                        mbtile
                          .interpretAs(mbtile.cellType.withNoData(noDataValue))
                      )
                    }
                    case None => {
                      logger.debug(
                        s"--CRITICAL MISS-- uri: ${uri}; zxy: $z/$x/$y"
                      )
                      None
                    }
                  }
                } else {
                  logger.debug(s"--MISS-- uri: ${uri}; zxy: $z/$x/$y")
                  None
                }
              }
          }
        val exportTile: IO[Raster[MultibandTile]] = subTilesIO.map { subtiles =>
          subtiles.flatten.reduceOption({ (t1, t2) =>
            logger.debug(
              s"Merging celltypes ct1: ${t1.cellType} ; ct2: ${t2.cellType}"
            )
            t1 merge t2
          }) match {
            case Some(mbtile) =>
              Raster(mbtile, extent)
            case None =>
              val tiles = (1 to bandCount).map { _ =>
                invisiTile
              }
              Raster(MultibandTile(tiles), extent)
          }
        }
        exportTile.map(ProjectedRaster(_, WebMercator))
      }
    }

  implicit val analysisExportTmsReification =
    new TmsReification[List[(String, Int, Option[Double])]] {
      def kind(): MamlKind =
        MamlKind.Image

      def tmsReification(
          self: List[(String, Int, Option[Double])],
          buffer: Int
      )(
          implicit contextShift: ContextShift[IO]
      ) = (z: Int, x: Int, y: Int) => {
        val layoutDefinition = getLayoutDefinition(z)
        val extent = layoutDefinition.mapTransform.keyToExtent(x, y)
        val subTilesIO: IO[List[Option[MultibandTile]]] = self.parTraverse {
          case (uri, band, _) =>
            IO {
              val rs = RasterSources
                .getOrUpdate(uri)
                .reproject(WebMercator, method = NearestNeighbor)
              if (rs.extent.intersects(extent)) {
                rs.reproject(WebMercator, method = NearestNeighbor)
                  .tileToLayout(layoutDefinition, NearestNeighbor)
                  .read(SpatialKey(x, y), Seq(band)) match {
                  case Some(tile) =>
                    logger.debug(
                      s"--HIT-- uri: ${uri}; celltype: ${tile.cellType}, zxy: $z/$x/$y"
                    )
                    val noDataValue = getNoDataValue(tile.cellType)
                    Some(
                      tile.interpretAs(tile.cellType.withNoData(noDataValue))
                    )
                  case None =>
                    logger.debug(s"--MISS-- uri: ${uri}; zxy: $z/$x/$y")
                    None
                }
              } else {
                logger.debug(s"--MISS-- uri: ${uri}; zxy: $z/$x/$y")
                None
              }
            }
        }
        val exportTile = subTilesIO.map { subtiles =>
          subtiles.flatten.reduceOption({ (t1, t2) =>
            logger.debug(
              s"Merging celltypes ct1: ${t1.cellType}; ct2: ${t2.cellType}"
            )
            t1 merge t2
          }) match {
            case Some(tile) =>
              Raster(tile, extent)
            case None =>
              Raster(MultibandTile(invisiTile), extent)
          }
        }
        exportTile.map(ProjectedRaster(_, WebMercator))
      }
    }
}
