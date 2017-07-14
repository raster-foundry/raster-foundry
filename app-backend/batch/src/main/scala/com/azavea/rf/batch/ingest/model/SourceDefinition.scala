package com.azavea.rf.batch.ingest.model

import io.circe.generic.JsonCodec
import com.azavea.rf.batch.util._
import com.azavea.rf.datamodel._
import com.azavea.rf.bridge._

import geotrellis.proj4.{CRS, LatLng}
import geotrellis.raster._
import geotrellis.vector._

import com.typesafe.scalalogging.LazyLogging
import java.net.URI

/** This class provides all information required to read from a source
  *
  * @param uri      The URI of the source imagery
  * @param extent   The Extent of a source tile
  * @param crs      The CRS of the projection to target in tiling
  * @param bandMaps A list of mappings from source to destination tile
  */
@JsonCodec
case class SourceDefinition(
  uri: URI,
  extent: Extent,
  extentCrs: CRS,
  crs: CRS,
  cellSize: CellSize,
  bandMaps: Array[BandMapping]
)

object SourceDefinition extends LazyLogging {
  /** This object handles deserialization of source definitions and overrides any values which
    *  might be found in the tiff's header.
    */
  @JsonCodec
  case class Overrides(
    uri: URI,
    optionExtent: Option[Extent],
    optionExtentCrs: Option[CRS],
    optionCrs: Option[CRS],
    optionCellSize: Option[CellSize],
    bandMaps: Array[BandMapping]
  ) {
    def toSourceDefinition: SourceDefinition = {
      (optionExtent, optionCrs, optionCellSize, optionExtentCrs) match {
        case (Some(extent), Some(crs), Some(cellSize), Some(extentCrs)) => {
          SourceDefinition(
            uri,
            extent,
            extentCrs,
            crs,
            cellSize,
            bandMaps
          )
        }
        case _ => {
          logger.debug(s"Reading tiff tags: $uri")
          val tt = getTiffTags(uri)
          SourceDefinition(
            uri,
            tt.extent,
            tt.crs,
            tt.crs,
            tt.cellSize,
            bandMaps
          )
        }
      }
    }
  }
}
