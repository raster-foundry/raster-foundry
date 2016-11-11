package com.azavea.rf.ingest.model

import java.net.URI
import spray.json._
import DefaultJsonProtocol._

import geotrellis.raster._
import geotrellis.vector._
import geotrellis.proj4.CRS

/** This class provides all information required to read from a source
  *
  * @param uri     The URI of the source imagery
  * @param extent  The Extent of a source tile
  * @param bandMaps A list of mappings from source to destination tile
  */
case class SourceDefinition(
  uri: URI,
  extent: Extent,
  bandMaps: Array[BandMapping]
)

object SourceDefinition {
  implicit val jsonFormat = jsonFormat3(SourceDefinition.apply _)
}
