package com.azavea.rf.datamodel

import com.azavea.rf.datamodel._
import io.circe.generic.JsonCodec

import java.util.UUID

/** The top level structure defining an export job
  *
  * @param id The UUID which identifies this particular ingest job
  * @param input [[InputDefinition]] with inforamation about input data for an export job
  * @param output [[OutputDefinition]] with inforamation about output data for an export job
  */
@JsonCodec
case class ExportDefinition(
  id: UUID,
  input: InputDefinition,
  output: OutputDefinition
)
