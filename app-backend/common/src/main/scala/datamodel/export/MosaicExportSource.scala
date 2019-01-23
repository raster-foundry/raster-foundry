package com.rasterfoundry.common.datamodel.export

import com.rasterfoundry.common._
import com.azavea.maml.ast.Expression
import com.azavea.maml.ast.codec.tree._
import geotrellis.vector.MultiPolygon
import geotrellis.proj4._
import cats.syntax.functor._
import _root_.io.circe._
import _root_.io.circe.syntax._
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
