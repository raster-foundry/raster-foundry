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

  implicit val layerAttributeHistogramStore: HistogramStore[LayerAttributeDao] =
    new HistogramStore[LayerAttributeDao] {
      def layerHistogram(self: LayerAttributeDao,
                         layerId: UUID,
                         subsetBands: List[Int]) = {
        self.getHistogram[Array[Histogram[Double]]](layerId, xa) map { hists =>
          subsetBands.toArray map { band =>
            hists(band)
          }
        }
      }

      def projectHistogram(
          self: LayerAttributeDao,
          projectId: UUID,
          subsetBands: List[Int]): IO[Array[Histogram[Double]]] = {
        self.getProjectHistogram[Array[Histogram[Double]]](projectId, xa) map {
          hists =>
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
            subsetBands.toArray map { band =>
              combinedHistogram(band)
            }
        }
      }
    }
}
