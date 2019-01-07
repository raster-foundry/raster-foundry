package com.rasterfoundry.backsplash

import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.histogram._
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

import com.rasterfoundry.backsplash.error._
import com.rasterfoundry.backsplash.HistogramStore.ToHistogramStoreOps

object BacksplashMosaic extends ToHistogramStoreOps {

  /** Filter out images that don't need to be included  */
  def filterRelevant(bsm: BacksplashMosaic): BacksplashMosaic = {
    var testMultiPoly: Option[MultiPolygon] = None

    bsm.filter({ bsi =>
      testMultiPoly match {
        case None =>
          testMultiPoly = Some(bsi.footprint)
          true
        case Some(mp) =>
          val cond = mp.covers(bsi.footprint)
          if (cond) {
            false
          } else {
            testMultiPoly = (mp union bsi.footprint) match {
              case PolygonResult(p)       => MultiPolygon(p).some
              case MultiPolygonResult(mp) => mp.some
              case _ =>
                throw new Exception(
                  "Should get a polygon or multipolygon, instead got no result")
            }
            true
          }
      }
    })
  }

  def first(bsm: BacksplashMosaic): IO[Option[BacksplashImage]] = {
    bsm
      .take(1)
      .compile
      .toList
      .map(_.headOption)
  }

  def layerHistogram(mosaic: BacksplashMosaic)(
      implicit hasRasterExtents: HasRasterExtents[BacksplashMosaic],
      extentReification: ExtentReification[BacksplashMosaic],
      cs: ContextShift[IO]) = {
    LayerHistogram.identity(mosaic, 4000)
  }

  def getStoreHistogram[T: HistogramStore](mosaic: BacksplashMosaic,
                                           histStore: T)(
      implicit hasRasterExtents: HasRasterExtents[BacksplashMosaic],
      extentReification: ExtentReification[BacksplashMosaic],
      cs: ContextShift[IO]): IO[List[Histogram[Double]]] =
    for {
      allImages <- filterRelevant(mosaic).compile.toList
      histArrays <- allImages traverse { im =>
        histStore.layerHistogram(im.imageId, im.subsetBands)
      }
      result <- histArrays match {
        case Nil =>
          layerHistogram(filterRelevant(mosaic)) map {
            case Valid(hists) => hists.toList
            case Invalid(e) =>
              throw MetadataException(
                s"Could not produce histograms: $e"
              )
          }
        case arrs =>
          val hists = arrs
            .foldLeft(
              Array.fill(arrs.head.length)(
                StreamingHistogram(255): Histogram[Double]))(
              (histArr1: Array[Histogram[Double]],
               histArr2: Array[Histogram[Double]]) => {
                histArr1 zip histArr2 map {
                  case (h1, h2) => h1 merge h2
                }
              }
            )
            .toList
          IO.pure { hists }
      }
    } yield {
      result
    }
}
