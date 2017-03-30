package com.azavea.rf.export.model

import spray.json.DefaultJsonProtocol._
import java.util.UUID

/** The top level structure defining an export job
  *
  * @param id The UUID which identifies this particular ingest job
  * @param input [[InputDefinition]] with inforamation about input data for an export job
  * @param output [[OutputDefinition]] with inforamation about output data for an export job
  */
case class ExportDefinition(
  id: UUID,
  input: InputDefinition,
  output: OutputDefinition
)

object ExportDefinition {
  implicit val jsonFormat = jsonFormat3(ExportDefinition.apply _)
}
