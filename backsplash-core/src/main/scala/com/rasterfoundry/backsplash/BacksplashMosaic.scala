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
}

