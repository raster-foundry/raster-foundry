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
            testMultiPoly = (mp union bsi.footprint) match {
              case PolygonResult(p) => MultiPolygon(p).some
              case MultiPolygonResult(mp) => mp.some
              case _ => throw new Exception("Should get a polygon or multipolygon, instead got no result")
            }
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

  implicit val hasRasterExtents: HasRasterExtents[BacksplashMosaic] = new HasRasterExtents[BacksplashMosaic] {
    def rasterExtents(self: BacksplashMosaic)(implicit contextShift: ContextShift[IO]): IO[NEL[RasterExtent]] = {
      filterRelevant(self)
        .flatMap({ img => fs2.Stream.eval(BacksplashImage.getRasterExtents(img.uri)) })
        .compile
        .toList
        .map(_.reduceLeft({ (nel1: NEL[RasterExtent], nel2: NEL[RasterExtent])  => nel1 concatNel nel2 }))
    }

    def crs(self: BacksplashMosaic)(implicit contextShift: ContextShift[IO]): IO[CRS] = ???
  }
  val impl = implicitly[ExtentReification[BacksplashMosaic]]
  //val impl = implicitly[HasRasterExtents[BacksplashMosaic]]
}

