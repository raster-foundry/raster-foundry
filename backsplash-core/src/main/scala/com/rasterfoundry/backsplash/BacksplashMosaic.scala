package com.rasterfoundry.backsplash

import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.server._

import com.azavea.maml.ast._
import com.azavea.maml.eval._

import cats._
import cats.implicits._
import cats.effect._

object BacksplashMosaic {

  /** Filter out images that don't need to be included  */
  def filterRelevant(bsm: BacksplashMosaic): BacksplashMosaic = {
    var testMultiPoly: Option[MultiPolygon] = None

    bsm.filter({ bsi =>
      testMultiPoly match {
        case None =>
          testMultiPoly = Some(MultiPolygon(bsi.footprint))
          true
        case Some(mp) =>
          val cond = mp.covers(bsi.footprint)
          if (cond) {
            false
          } else {
            testMultiPoly = (mp union bsi.footprint).as[MultiPolygon]
            true
          }
      }
    })
  }

  val backsplashMosaicReification = new TmsReification[BacksplashMosaic] {
    def kind(self: BacksplashMosaic): MamlKind = MamlKind.Image

    def tmsReification(self: BacksplashMosaic, buffer: Int)(implicit contextShift: ContextShift[IO]): (Int, Int, Int) => IO[Literal] =
      (z: Int, x: Int, y: Int) => {
        val extent = BacksplashImage.tmsLevels(z).mapTransform.keyToExtent(x, y)
        val bandCount = self.take(1).map(_.subsetBands.length).compile.fold(0)(_ + _).unsafeRunSync
        val ndtile: MultibandTile = ArrayMultibandTile.empty(IntConstantNoDataCellType, bandCount, 256, 256)
        filterRelevant(self)
          .map(_.read(z, x, y))
          .map({ maybeMBTile =>
            val mbtile = maybeMBTile.getOrElse {
              throw new Exception(s"really expected to find a tile at $z, $x, $y for $self")
            }
            Raster(mbtile, extent)
          }).compile
            .fold(Raster(ndtile, extent))({ (mbr1, mbr2) =>
            mbr1.merge(mbr2, NearestNeighbor)
          }).map(RasterLit(_))
    }
  }

  implicit val extentReification: ExtentReification[BacksplashMosaic] = new ExtentReification[BacksplashMosaic] {
    def kind(self: BacksplashMosaic): MamlKind = MamlKind.Image

    def extentReification(self: BacksplashMosaic)(implicit contextShift: ContextShift[IO]) =
      (extent: Extent, cs: CellSize) => {
        val bands = self.take(1).map(_.subsetBands).compile.toList.map(_.flatten).unsafeRunSync
        val ndtile: MultibandTile = ArrayMultibandTile.empty(IntConstantNoDataCellType, bands.length, 256, 256)
        filterRelevant(self)
          .map(_.read(extent, cs))
          .map({ maybeMBTile =>
            val mbtile = maybeMBTile.getOrElse {
              throw new Exception(s"really expected to find a tile for $extent and $cs for $self")
            }
            Raster(mbtile, extent)
          }).compile
            .fold(Raster(ndtile, extent))({ (mbr1, mbr2) =>
            mbr1.merge(mbr2, NearestNeighbor)
          }).map(RasterLit(_))
    }
  }
}

