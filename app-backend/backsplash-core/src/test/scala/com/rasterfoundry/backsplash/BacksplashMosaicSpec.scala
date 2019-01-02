package com.rasterfoundry.backsplash

import geotrellis.server._
import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import cats.data.Validated._
import cats.effect.IO
import org.scalatest._
import org.scalatest.prop.Checkers
import org.scalacheck.Prop.forAll
import com.azavea.maml.ast._

import BacksplashImageGen._

import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors
import java.util.UUID

class BacksplashMosaicSpec extends FunSuite with Checkers with Matchers {
  val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))
  implicit val timer = IO.timer(ec)

  implicit val nothingHistStore: HistogramStore[Nothing] =
    new HistogramStore[Nothing] {
      def layerHistogram(self: Nothing, layerId: UUID, subsetBands: List[Int]) =
        ???
    }

  val mosaicImplicits =
    new MosaicImplicits[Nothing](new MetricsRegistrator(), ???)
  import mosaicImplicits._
  implicit val cs = IO.contextShift(ExecutionContext.global)

  test("remove unnecessary images from mosaic") {
    check {
      forAll { (img1: BacksplashImage, img2: BacksplashImage) =>
        var count = 0
        def work = { count = count + 1 }

        val mosaic = fs2.Stream.emits(List(img1, img2)).repeat.take(50)
        val relevantStream = BacksplashMosaic.filterRelevant(mosaic)

        relevantStream
          .map({ _ =>
            work
          })
          .compile
          .drain
          .unsafeRunSync

        if (img1 == img2) {
          assert(count == 1, s"Expected 1, got $count units of work")
        } else {
          assert(count == 2, s"Expected 2, got $count units of work")
        }
        true
      }
    }
  }

  test(
    "fetching mosaics should return real tiles (diff min/max value as proxy)") {
    check {
      forAll { (mosaic: BacksplashMosaic) =>
        {
          val hists =
            LayerHistogram.identity(mosaic, 4000).unsafeRunSync match {
              case Valid(hists) => hists
              case Invalid(_) =>
                throw new Exception("could not resolve histograms for mosaic")
            }
          assert(hists.head.minValue != hists.head.maxValue)
          true
        }
      }
    }
  }

  /** Use only when you want to write some imagery out to disk during testing, to veriy
    * that it looks nice
    */
  ignore("writeExtent") {
    check {
      forAll { (mosaic: BacksplashMosaic) =>
        val eval = mosaic.extentReification
        val e = Extent(-147.34863281250003,
                       20.014645445341365,
                       -83.40820312500001,
                       49.97948776108648)
        val rlit = eval(e, CellSize(10, 10)).unsafeRunSync
        val tileID = java.util.UUID.randomUUID
        val outputPath = s"/tmp/${tileID}.tif"
        MultibandGeoTiff(
          rlit.asInstanceOf[RasterLit[Raster[MultibandTile]]].raster,
          geotrellis.proj4.WebMercator)
          .write(outputPath)
        println(s"Extent-based tiff produced at $outputPath")
        true
      }
    }
  }

  /** Use only when you want to write some imagery out to disk during testing, to veriy
    * that it looks nice
    */
  ignore("writeTMS") {
    check {
      forAll { (mosaic: BacksplashMosaic) =>
        val eval = mosaic.tmsReification(0)
        val rlit = eval(7, 24, 48).unsafeRunSync
        val tileID = java.util.UUID.randomUUID
        val outputPath = s"/tmp/${tileID}.tif"
        MultibandGeoTiff(
          rlit.asInstanceOf[RasterLit[Raster[MultibandTile]]].raster,
          geotrellis.proj4.WebMercator)
          .write(outputPath)
        println(s"TMS-based tiff produced at $outputPath")
        true
      }
    }
  }
}
