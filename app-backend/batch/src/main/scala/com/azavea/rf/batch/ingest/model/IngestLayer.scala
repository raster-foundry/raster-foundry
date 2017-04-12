package com.azavea.rf.batch.ingest.model

import io.circe._
import io.circe.syntax._
import cats.syntax.either._

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
  implicit val ingestLayerEncoder: Encoder[IngestLayer] =
    Encoder.encodeString.contramap[IngestLayer] { il =>
      JsonObject.fromMap(
        Map(
          "id" -> il.id.asJson,
          "output" -> il.output.asJson,
          "sources" -> il.sources.asJson
        )
      ).asJson.noSpaces
    }

  implicit val ingestLayerDecoder: Decoder[IngestLayer] =
    Decoder[Json] emap { js =>
      js.as[JsonObject].map { jso =>
        val map = jso.toMap
        (map.get("id"), map.get("output"), map.get("sources")) match {
          case (Some(id), Some(output), Some(sources)) => {
            (id.as[UUID].toOption, output.as[OutputDefinition].toOption, sources.as[Array[SourceDefinition.Overrides]].toOption) match {
              case (Some(id), Some(output), Some(sources)) =>
                IngestLayer(
                  id = id,
                  output = output,
                  sources = sources.map(_.toSourceDefinition)
                )
              case _ => throw new Exception("Can't decode IngestLayer")
            }
          }
          case _ => throw new Exception("Can't decode IngestLayer")
        }
      } leftMap (_ => "IngestLayer")
    }
}

