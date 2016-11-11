package com.azavea.rf.ingest.model

import spray.json._
import DefaultJsonProtocol._
import geotrellis.vector.Extent

import java.util.UUID
import java.net.URI

/** The top level structure defining an ingest job
  *
  * @param id The UUID which identifies this particular ingest job
  * @param layers A list of [[IngestLayer]] definitions
  */
case class IngestDefinition(id: UUID, layers: Array[IngestLayer])

object IngestDefinition {
  implicit val jsonFormat = jsonFormat2(IngestDefinition.apply _)
}
