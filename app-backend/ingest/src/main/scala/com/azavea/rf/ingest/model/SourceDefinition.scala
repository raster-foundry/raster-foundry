package com.azavea.rf.ingest.model


import java.net.URI
import spray.json._
import DefaultJsonProtocol._

import geotrellis.raster._
import geotrellis.vector._
import geotrellis.proj4.CRS

/** This class provides all information required to read from a source */
case class SourceDefinition(
  uri: URI,
  extent: Extent,
  bandMaps: Array[BandMapping]
)

object SourceDefinition {
  implicit val jsonFormat = jsonFormat3(SourceDefinition.apply _)
}
