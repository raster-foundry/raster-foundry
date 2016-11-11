package com.azavea.rf.ingest.model

import spray.json._
import DefaultJsonProtocol._

import java.util.UUID

/** An ingest layer groups together input sources which should be within the same catalog
  *
  * @param id A UUID for this particular layer (used later in providing a key to the GT catalog)
  * @param output An [[OutputDefinition]] which specifies how to save a set of tiles
  * @param sources A list of source specifications
  */
case class IngestLayer(
  id: UUID,
  output: OutputDefinition,
  sources: Array[SourceDefinition]
)

object IngestLayer {
  implicit val jsonFormat = jsonFormat3(IngestLayer.apply _)
}
