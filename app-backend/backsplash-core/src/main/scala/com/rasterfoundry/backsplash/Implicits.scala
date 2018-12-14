package com.rasterfoundry.backsplash

import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.proj4.CRS
import geotrellis.server._

import com.azavea.maml.ast._
import com.azavea.maml.eval._

import cats._
import cats.implicits._
import cats.data.{NonEmptyList => NEL}
import cats.data.Validated._
import cats.effect._

import ProjectStore._
import ToolStore._
import ExtentReification._
import HasRasterExtents._
import TmsReification._

class MosaicImplicits(mtr: MetricsRegistrator)
    extends ToTmsReificationOps
    with ToExtentReificationOps
    with ToHasRasterExtentsOps
    with ToProjectStoreOps
    with ToToolStoreOps {

  val readMosaicTimer =
    mtr.newTimer(classOf[MosaicImplicits], "read-mosaic")
  val readSceneTmsTimer =
    mtr.newTimer(classOf[MosaicImplicits], "read-tms-scene")
  val readSceneExtentTimer =
    mtr.newTimer(classOf[MosaicImplicits], "read-extent-scene")

  implicit val mosaicTmsReification = new TmsReification[BacksplashMosaic] {
    def kind(self: BacksplashMosaic): MamlKind = MamlKind.Image

    def tmsReification(self: BacksplashMosaic, buffer: Int)(
        implicit contextShift: ContextShift[IO])
      : (Int, Int, Int) => IO[Literal] =
      (z: Int, x: Int, y: Int) =>
        for {
          bandCount <- self
            .take(1)
            .map(_.subsetBands.length)
            .compile
            .fold(0)(_ + _)
          extent = BacksplashImage.tmsLevels(z).mapTransform.keyToExtent(x, y)
          ndtile: MultibandTile = ArrayMultibandTile.empty(
            IntConstantNoDataCellType,
            bandCount,
            256,
            256
          )
          mosaic = BacksplashMosaic
            .filterRelevant(self)
            .map({ relevant =>
              val time = readSceneTmsTimer.time()
              val img = relevant.read(z, x, y)
              time.stop()
              img
            })
            .collect({ case Some(mbtile) => Raster(mbtile, extent) })
            .compile
            .fold(Raster(ndtile, extent))({ (mbr1, mbr2) =>
              mbr1.merge(mbr2, NearestNeighbor)
            })
          timedMosaic <- mtr.timedIO(mosaic, readMosaicTimer)
        } yield RasterLit(timedMosaic)
  }

  implicit val mosaicExtentReification: ExtentReification[BacksplashMosaic] =
    new ExtentReification[BacksplashMosaic] {
      def kind(self: BacksplashMosaic): MamlKind = MamlKind.Image

      def extentReification(self: BacksplashMosaic)(
          implicit contextShift: ContextShift[IO]) =
        (extent: Extent, cs: CellSize) =>
          for {
            bands <- self
              .take(1)
              .map(_.subsetBands)
              .compile
              .toList
              .map(_.flatten)
            ndtile: MultibandTile = ArrayMultibandTile.empty(
              IntConstantNoDataCellType,
              bands.length,
              256,
              256
            )
            mosaic = BacksplashMosaic
              .filterRelevant(self)
              .map({ relevant =>
                val time = readSceneExtentTimer.time()
                val img = relevant.read(extent, cs)
                time.stop()
                img
              })
              .collect({ case Some(mbtile) => Raster(mbtile, extent) })
              .compile
              .fold(Raster(ndtile, extent))({ (mbr1, mbr2) =>
                mbr1.merge(mbr2, NearestNeighbor)
              })
            timedMosaic <- mtr.timedIO(mosaic, readMosaicTimer)
          } yield RasterLit(timedMosaic)
    }

  implicit val mosaicHasRasterExtents: HasRasterExtents[BacksplashMosaic] =
    new HasRasterExtents[BacksplashMosaic] {
      def rasterExtents(self: BacksplashMosaic)(
          implicit contextShift: ContextShift[IO]): IO[NEL[RasterExtent]] = {
        val mosaic = BacksplashMosaic
          .filterRelevant(self)
          .flatMap({ img =>
            fs2.Stream.eval(BacksplashImage.getRasterExtents(img.uri))
          })
          .compile
          .toList
          .map(_.reduceLeft({
            (nel1: NEL[RasterExtent], nel2: NEL[RasterExtent]) =>
              nel1 concatNel nel2
          }))
        mtr.timedIO(mosaic, readMosaicTimer)
      }
    }
}
