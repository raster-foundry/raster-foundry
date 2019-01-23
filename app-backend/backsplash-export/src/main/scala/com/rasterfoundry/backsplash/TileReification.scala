package com.rasterfoundry.backsplash.export

import geotrellis.raster._
import geotrellis.raster.resample._
import geotrellis.proj4._
import geotrellis.spark.SpatialKey
import geotrellis.spark.tiling._
import geotrellis.server._
import com.azavea.maml.ast._
import cats.effect._
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging

object TileReification extends LazyLogging {
  val tmsLevels: Array[LayoutDefinition] = {
    val scheme = ZoomedLayoutScheme(WebMercator, 256)
    for (zoom <- 0 to 64) yield scheme.levelForZoom(zoom).layout
  }.toArray

  private def md5Hash(text: String): String =
    java.security.MessageDigest
      .getInstance("MD5")
      .digest(text.getBytes())
      .map(0xFF & _)
      .map { "%02x".format(_) }
      .foldLeft("") { _ + _ }

  val invisiCellType = DoubleConstantNoDataCellType
  val invisiTile = ArrayTile.empty(invisiCellType, 256, 256)

  implicit val mosaicExportTmsReification =
    new TmsReification[List[(String, List[Int], Option[Double])]] {
      def kind(self: List[(String, List[Int], Option[Double])]): MamlKind =
        MamlKind.Image

      def tmsReification(self: List[(String, List[Int], Option[Double])],
                         buffer: Int)(
          implicit contextShift: ContextShift[IO]
      ): (Int, Int, Int) => IO[Literal] = (z: Int, x: Int, y: Int) => {
        val ld = tmsLevels(z)
        val extent = ld.mapTransform.keyToExtent(x, y)
        val bandCount = self.head._2.length
        val subTilesIO: IO[List[Option[MultibandTile]]] =
          self.traverse {
            case (uri, bands, ndOverride) =>
              IO {
                getRasterSource(uri)
                  .reproject(WebMercator, NearestNeighbor)
                  .tileToLayout(ld, NearestNeighbor)
                  .read(SpatialKey(x, y), bands) match {
                  case Some(mbtile) =>
                    logger.debug(
                      s"--HIT-- uri: ${uri}; celltype: ${mbtile.cellType}, zxy: $z/$x/$y")
                    logger.trace(s"b1 hash: ${md5Hash(
                      mbtile.band(0).toArray.take(100).mkString(""))}, vals ${mbtile.band(0).toArray.take(10).toList}")
                    logger.trace(s"b2 hash: ${md5Hash(
                      mbtile.band(1).toArray.take(100).mkString(""))}, vals ${mbtile.band(1).toArray.take(10).toList}")
                    logger.trace(s"b3 hash: ${md5Hash(
                      mbtile.band(2).toArray.take(100).mkString(""))}, vals ${mbtile.band(2).toArray.take(10).toList}")

                    ndOverride.map { nd =>
                      val currentCT = mbtile.cellType
                      mbtile.interpretAs(currentCT.withNoData(Some(nd)))
                    } orElse {
                      Some(mbtile)
                    }
                  case None =>
                    logger.debug(s"--MISS-- uri: ${uri}; zxy: $z/$x/$y")
                    None
                }
              }
          }
        val exportTile: IO[Raster[MultibandTile]] = subTilesIO.map { subtiles =>
          subtiles.flatten.reduceOption({ (t1, t2) =>
            logger.debug(
              s"Merging celltypes ct1: ${t1.cellType}; ct2: ${t2.cellType}")
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
        exportTile.map(RasterLit(_))
      }
    }

  implicit val analysisExportTmsReification =
    new TmsReification[List[(String, Int, Option[Double])]] {
      def kind(self: List[(String, Int, Option[Double])]): MamlKind =
        MamlKind.Image

      def tmsReification(self: List[(String, Int, Option[Double])],
                         buffer: Int)(
          implicit contextShift: ContextShift[IO]
      ): (Int, Int, Int) => IO[Literal] = (z: Int, x: Int, y: Int) => {
        val ld = tmsLevels(z)
        val extent = ld.mapTransform.keyToExtent(x, y)
        val subTilesIO: IO[List[Option[MultibandTile]]] = self.traverse {
          case (uri, band, ndOverride) =>
            IO {
              getRasterSource(uri)
                .reproject(WebMercator, NearestNeighbor)
                .tileToLayout(ld, NearestNeighbor)
                .read(SpatialKey(x, y), Seq(band)) match {
                case Some(tile) =>
                  logger.debug(
                    s"--HIT-- uri: ${uri}; celltype: ${tile.cellType}, zxy: $z/$x/$y")
                  ndOverride.map { nd =>
                    val currentCT = tile.cellType
                    tile.interpretAs(currentCT.withNoData(Some(nd)))
                  } orElse {
                    Some(tile)
                  }
                case None =>
                  logger.debug(s"--MISS-- uri: ${uri}; zxy: $z/$x/$y")
                  None
              }
            }
        }
        val exportTile = subTilesIO.map { subtiles =>
          subtiles.flatten.reduceOption({ (t1, t2) =>
            logger.debug(
              s"Merging celltypes ct1: ${t1.cellType}; ct2: ${t2.cellType}")
            t1 merge t2
          }) match {
            case Some(tile) =>
              Raster(tile, extent)
            case None =>
              Raster(MultibandTile(invisiTile), extent)
          }
        }
        exportTile.map(RasterLit(_))
      }
    }
}
