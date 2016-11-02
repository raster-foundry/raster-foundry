package com.azavea.rf.ingest.model

import spray.json._
import DefaultJsonProtocol._

import java.util.UUID

/** An ingest layer groups together input sources which should be
 *   within the same catalog
 */
case class IngestLayer(
  id: UUID,
  output: OutputDefinition,
  sources: Array[SourceDefinition]
)

object IngestLayer {
  implicit val jsonFormat = jsonFormat3(IngestLayer.apply _)
}
