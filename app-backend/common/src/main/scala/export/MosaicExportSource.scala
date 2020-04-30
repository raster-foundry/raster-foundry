package com.rasterfoundry.common.export

import _root_.io.circe.generic.semiauto._
import geotrellis.vector.MultiPolygon
import geotrellis.vector.io.json.Implicits._

final case class MosaicExportSource(
    zoom: Int,
    area: MultiPolygon,
    layers: List[(String, List[Int], Option[Double])]
)

object MosaicExportSource {
  implicit val encoder = deriveEncoder[MosaicExportSource]
  implicit val decoder = deriveDecoder[MosaicExportSource]
}
