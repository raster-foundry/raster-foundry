package com.azavea.rf.ingest.model

import geotrellis.proj4.CRS
import geotrellis.raster._
import spray.json._
import DefaultJsonProtocol._
import java.net.URI

/** This class holds the information necessary to take a series of inputs and store them in a shared catalog
  *
  * @param uri              The URI which tells an ingest where to persist this layer
  * @param crs              The CRS of the projection to target in tiling
  * @param cellType         A GeoTrellis CellType to be used in storing a tile layer
  * @param cellSize         The expected 'native resolution' size of each cell
  * @param histogramProjects The number of bins with which to construct histograms used in coloring
  * @param pyramid          Whether or not to pyramid multiple zoom levels on ingest
  * @param native           Whether or not to keep around a copy of the 'native resolution' images
  */
case class OutputDefinition(
  uri: URI,
  crs: CRS,
  cellType: CellType,
  cellSize: CellSize,
  histogramProjects: Int = 256,
  pyramid: Boolean,
  native: Boolean
)

object OutputDefinition {
  implicit val jsonFormat = jsonFormat7(OutputDefinition.apply _)
}
