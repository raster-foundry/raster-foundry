package com.azavea.rf.ingest.model

import geotrellis.proj4.CRS
import geotrellis.raster._
import spray.json._
import DefaultJsonProtocol._
import java.net.URI

/** This class holds the information necessary to take a series of
 *   inputs and store them in a shared catalog
 */
case class OutputDefinition(
  uri: URI,
  crs: CRS,
  cellType: CellType,
  cellSize: CellSize,
  pyramid: Boolean,
  native: Boolean
)

object OutputDefinition {
  implicit val jsonFormat = jsonFormat6(OutputDefinition.apply _)
}
