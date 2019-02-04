package com.rasterfoundry.backsplash.server

import cats.effect.IO
import com.rasterfoundry.backsplash.HistogramStore
import com.rasterfoundry.backsplash.HistogramStore.ToHistogramStoreOps
import com.rasterfoundry.database.LayerAttributeDao
import doobie.Transactor
import geotrellis.raster.histogram._
import geotrellis.raster.io.json._
import geotrellis.spark.LayerId

import java.util.UUID

import spray.json._
import DefaultJsonProtocol._

trait HistogramStoreImplicits
    extends ToHistogramStoreOps
    with HistogramJsonFormats {

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

  implicit val layerAttributeHistogramStore: HistogramStore[LayerAttributeDao] =
    new HistogramStore[LayerAttributeDao] {
      def layerHistogram(self: LayerAttributeDao,
                         layerId: UUID,
                         subsetBands: List[Int]) = {
        self.getHistogram(layerId, xa) map { hists =>
          subsetBands.toArray map { band =>
            hists(band)
          }
        }
      }

      def projectLayerHistogram(
          self: LayerAttributeDao,
          projectLayerId: UUID,
          subsetBands: List[Int]
      ): IO[Array[Histogram[Double]]] = {
        self.getProjectLayerHistogram(projectLayerId, xa) map { hists =>
          mergeHistsForBands(subsetBands, hists)
        }
      }
    }
}
