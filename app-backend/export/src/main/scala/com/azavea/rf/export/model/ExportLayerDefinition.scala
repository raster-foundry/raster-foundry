package com.azavea.rf.export.model

import com.azavea.rf.datamodel.ColorCorrect
import spray.json.DefaultJsonProtocol._
import java.net.URI
import java.util.UUID

case class ExportLayerDefinition(
  layerId: UUID,
  ingestLocation: URI,
  colorCorrections: Option[ColorCorrect.Params]
)

object ExportLayerDefinition {
  implicit val jsonFormat = jsonFormat3(ExportLayerDefinition.apply _)
}
