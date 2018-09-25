package com.azavea.rf.datamodel

import java.util.UUID

import io.circe.generic.JsonCodec

/** The top level structure defining an export job
  *
  * @param id The UUID which identifies this particular export job
  * @param input [[InputDefinition]] with information about input data for an export job
  * @param output [[OutputDefinition]] with information about output data for an export job
  */
@JsonCodec
final case class ExportDefinition(id: UUID,
                                  input: InputDefinition,
                                  output: OutputDefinition)
