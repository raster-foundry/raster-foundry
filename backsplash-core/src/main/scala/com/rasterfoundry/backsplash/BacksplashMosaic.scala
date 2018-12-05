package com.rasterfoundry.backsplash

import geotrellis.raster._
import geotrellis.vector._
import geotrellis.server._
import com.azavea.maml.ast._

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
      (z: Int, x: Int, y: Int) => ???
  }

  implicit val extentReification: ExtentReification[BacksplashMosaic] = new ExtentReification[BacksplashMosaic] {
    def kind(self: BacksplashMosaic): MamlKind = MamlKind.Image

    def extentReification(self: BacksplashMosaic)(implicit contextShift: ContextShift[IO]) =
      (extent: Extent, cellSize: CellSize) => ???
  }
}

