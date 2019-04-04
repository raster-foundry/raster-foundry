package com.rasterfoundry.backsplash.server

import com.rasterfoundry.backsplash.HistogramStore
import com.rasterfoundry.backsplash.HistogramStore.ToHistogramStoreOps
import com.rasterfoundry.database.LayerAttributeDao

import cats.effect.IO
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import doobie.Transactor
import geotrellis.raster.histogram._
import geotrellis.raster.io.json._

import java.util.UUID

trait HistogramStoreImplicits
    extends ToHistogramStoreOps
    with HistogramJsonFormats
    with LazyLogging {

  val xa: Transactor[IO]

  @SuppressWarnings(Array("TraversableHead"))
  private def mergeHistsForBands(
      bands: List[Int],
      hists: List[Array[Histogram[Double]]]): Array[Histogram[Double]] = {
    val combinedHistogram = hists.foldLeft(
      Array.fill(hists.head.length)(
        StreamingHistogram(255): Histogram[Double]))(
      (histArr1: Array[Histogram[Double]],
       histArr2: Array[Histogram[Double]]) => {
        histArr1 zip histArr2 map {
          case (h1, h2) => h1 merge h2
        }
      }
    )
    bands.toArray map { band =>
      combinedHistogram(band)
    }
  }

  private def handleBandsOutOfRange(
      result: Either[Throwable, Array[Histogram[Double]]],
      layerId: UUID,
      subsetBands: List[Int]) = result match {
    case Left(e: ArrayIndexOutOfBoundsException) =>
      logger.warn(
        s"Requested bands not available in layer $layerId: $subsetBands")
      IO { Array.empty[Histogram[Double]] }
    case Left(e) =>
      IO.raiseError(e)
    case Right(hists) =>
      IO.pure { hists }
  }

  implicit val layerAttributeHistogramStore: HistogramStore[LayerAttributeDao] =
    new HistogramStore[LayerAttributeDao] {
      def layerHistogram(self: LayerAttributeDao,
                         layerId: UUID,
                         subsetBands: List[Int]) = {
        self
          .getHistogram(layerId, xa)
          .map({ (hists: Array[Histogram[Double]]) =>
            subsetBands.toArray map { band =>
              hists(band)
            }
          })
          .attempt
      } flatMap { handleBandsOutOfRange(_, layerId, subsetBands) }

      def projectLayerHistogram(
          self: LayerAttributeDao,
          projectLayerId: UUID,
          subsetBands: List[Int]
      ): IO[Array[Histogram[Double]]] = {
        self
          .getProjectLayerHistogram(projectLayerId, xa)
          .map({ hists =>
            mergeHistsForBands(subsetBands, hists)
          })
          .attempt flatMap {
          handleBandsOutOfRange(_, projectLayerId, subsetBands)
        }
      }
    }
}
