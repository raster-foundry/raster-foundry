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
    }
}
