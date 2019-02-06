package com.rasterfoundry.common.datamodel.export

import com.rasterfoundry.common._
import com.azavea.maml.ast.Expression
import com.azavea.maml.ast.codec.tree._
import geotrellis.vector.MultiPolygon
import _root_.io.circe.generic.semiauto._

case class AnalysisExportSource(
    zoom: Int,
    area: MultiPolygon,
    ast: Expression,
    params: Map[String, List[(String, Int, Option[Double])]]
)

object AnalysisExportSource {
  implicit val encoder = deriveEncoder[AnalysisExportSource]
  implicit val decoder = deriveDecoder[AnalysisExportSource]
}
