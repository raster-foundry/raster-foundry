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
  implicit object ingestLayerJsonFormat extends RootJsonFormat[IngestLayer] {
      def write(obj: IngestLayer) = JsObject(
        "id" -> obj.id.toJson,
        "output" -> obj.output.toJson,
        "sources" -> obj.sources.toJson
      )
      def read(json: JsValue) = json.asJsObject.getFields("id", "output", "sources") match {
        case Seq(i, o, s) =>
          IngestLayer(
            id = i.convertTo[UUID],
            output = o.convertTo[OutputDefinition],
            sources = s.convertTo[Array[SourceDefinition.Overrides]].map(_.toSourceDefinition))
        case _ =>
          deserializationError("Failed to parse IngestLayer")
      }

  }

}
