package com.azavea.rf.ingest.model

import geotrellis.proj4.CRS
import geotrellis.raster._
import geotrellis.raster.resample._
import geotrellis.spark.io.{CRSFormat => _, _} // We must shade the default CRSFormat
import geotrellis.spark._
import geotrellis.spark.io.index._

import spray.json._
import DefaultJsonProtocol._
import java.net.URI

/** This class holds the information necessary to take a series of inputs and store them in a shared catalog
  *
  * @param uri              The URI which tells an ingest where to persist this layer
  * @param crs              The CRS of the projection to target in tiling
  * @param cellType         A GeoTrellis CellType to be used in storing a tile layer
  * @param cellSize         The expected 'native resolution' size of each cell
  * @param histogramBuckets The number of bins with which to construct histograms used in coloring
  * @param pyramid          Whether or not to pyramid multiple zoom levels on ingest
  * @param native           Whether or not to keep around a copy of the 'native resolution' images
  * @param resampleMethod   The type of GeoTrellis resample method to use for cell value estimation
  * @param keyIndexMethod   The GeoTrellis KeyIndexMethod to use when writing output indices
  */
case class OutputDefinition(
  uri: URI,
  crs: CRS,
  cellType: CellType,
  histogramBuckets: Int = 256,
  tileSize: Int = 256,
  pyramid: Boolean = true,
  native: Boolean = false,
  resampleMethod: ResampleMethod = NearestNeighbor,
  keyIndexMethod: KeyIndexMethod[SpatialKey] = ZCurveKeyIndexMethod // TODO: read, no write
)

object OutputDefinition {
  implicit val jsonFormat = jsonFormat9(OutputDefinition.apply _)
}
