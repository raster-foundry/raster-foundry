package com.azavea.rf.batch.ingest.model

import io.circe.generic.JsonCodec

import java.util.UUID

/** The top level structure defining an ingest job
  *
  * @param id The UUID which identifies this particular ingest job
  * @param layers A list of [[IngestLayer]] definitions
  */
@JsonCodec
case class IngestDefinition(id: UUID, layers: Array[IngestLayer])
