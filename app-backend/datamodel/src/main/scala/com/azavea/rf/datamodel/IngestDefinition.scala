package com.azavea.rf.datamodel

import spray.json._
import spray.json.DefaultJsonProtocol._

import java.util.UUID

case class IngestDefinition(
	name: String,
  id: UUID,
  scenes: Array[Scene.WithRelated]
)

object IngestDefinition {
  implicit val defaultIngestJobFormat = jsonFormat3(IngestDefinition.apply _)
}
