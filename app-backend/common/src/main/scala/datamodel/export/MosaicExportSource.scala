package com.rasterfoundry.common.datamodel.export

import com.rasterfoundry.common._
import geotrellis.vector.MultiPolygon
import _root_.io.circe.generic.semiauto._

case class MosaicExportSource(
    zoom: Int,
    area: MultiPolygon,
    layers: List[(String, List[Int], Option[Double])]
)

object MosaicExportSource {
  implicit val encoder = deriveEncoder[MosaicExportSource]
  implicit val decoder = deriveDecoder[MosaicExportSource]
}
