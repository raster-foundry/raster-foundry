package com.azavea.rf.ingest.tool

import java.net.URI
import spray.json._
import DefaultJsonProtocol._

import geotrellis.raster._
import geotrellis.vector._
import geotrellis.proj4.CRS

/** This class provides all information required to read from a source
  *
  * @param uri      The URI of the source imagery
  * @param extent   The Extent of a source tile
  * @param crs      The CRS of the projection to target in tiling
  * @param bandMaps A list of mappings from source to destination tile
  */
case class SourceDefinition(
  uri: URI,
  extent: Extent,
  crsExtent: Option[CRS],
  crs: CRS,
  cellSize: CellSize,
  bandMaps: Array[BandMapping]
) {
  def getCRSExtent: CRS = crsExtent match {
    case Some(crs) => crs
    case None => this.crs
  }
}

object SourceDefinition {
  implicit val jsonFormat = jsonFormat6(SourceDefinition.apply _)
}
