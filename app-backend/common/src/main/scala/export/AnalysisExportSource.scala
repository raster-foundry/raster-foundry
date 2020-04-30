package com.rasterfoundry.common.export

import com.azavea.maml.ast.Expression
import com.azavea.maml.ast.codec.tree.ExpressionTreeCodec
import geotrellis.vector.MultiPolygon
import geotrellis.vector.io.json.Implicits._
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}

final case class AnalysisExportSource(
    zoom: Int,
    area: MultiPolygon,
    ast: Expression,
    params: Map[String, List[(String, Int, Option[Double])]]
)

object AnalysisExportSource extends ExpressionTreeCodec {
  implicit val encoder: Encoder.AsObject[AnalysisExportSource] =
    deriveEncoder[AnalysisExportSource]
  implicit val decoder: Decoder[AnalysisExportSource] =
    deriveDecoder[AnalysisExportSource]
}
