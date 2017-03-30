package com.azavea.rf.export.model

import spray.json.DefaultJsonProtocol._

import java.net.URI
import java.util.UUID

case class ExportLayerDefinition(
  layerId: UUID,
  ingestLocation: URI,
  attributes: List[String] // some attributes
)

object ExportLayerDefinition {
  implicit val jsonFormat = jsonFormat3(ExportLayerDefinition.apply _)
}
