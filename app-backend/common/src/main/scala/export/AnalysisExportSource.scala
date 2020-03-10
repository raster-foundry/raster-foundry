package com.rasterfoundry.common.export

import com.azavea.maml.ast.Expression
import com.azavea.maml.ast.codec.tree.ExpressionTreeCodec
import geotrellis.vector.MultiPolygon
import io.circe.generic.semiauto._
import io.circe.{Decoder, ObjectEncoder}

final case class AnalysisExportSource(
    zoom: Int,
    area: MultiPolygon,
    ast: Expression,
    params: Map[String, List[(String, Int, Option[Double])]]
)

object AnalysisExportSource extends ExpressionTreeCodec {
  implicit val encoder: ObjectEncoder[AnalysisExportSource] =
    deriveEncoder[AnalysisExportSource]
  implicit val decoder: Decoder[AnalysisExportSource] =
    deriveDecoder[AnalysisExportSource]
}
