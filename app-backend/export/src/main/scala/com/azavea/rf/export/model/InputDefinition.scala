package com.azavea.rf.export.model

import geotrellis.vector._
import geotrellis.vector.io._

import spray.json.DefaultJsonProtocol._

import java.util.UUID

case class InputDefinition(
  projectId: UUID,
  resolution: Int,
  layers: Array[ExportLayerDefinition],
  mask: Option[MultiPolygon]
)

object InputDefinition {
  implicit val jsonFormat = jsonFormat4(InputDefinition.apply _)
}
