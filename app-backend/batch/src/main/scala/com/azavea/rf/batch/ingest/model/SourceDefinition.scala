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
    extent: Option[Extent],
    extentCrs: Option[CRS],
    crs: Option[CRS],
    cellSize: Option[CellSize],
    bandMaps: Array[BandMapping]
  ) {
    @SuppressWarnings(Array("OptionGet"))
    def toSourceDefinition: SourceDefinition = {
      if (extent.isDefined && crs.isDefined && cellSize.isDefined) {
        SourceDefinition(
          uri,
          extent.get,
          extentCrs.getOrElse(LatLng),
          crs.get,
          cellSize.get,
          bandMaps
        )
      } else {
        logger.debug(s"Reading tiff tags: $uri")
        lazy val tt = getTiffTags(uri)
        SourceDefinition(
          uri,
          extent.getOrElse(tt.extent),
          extentCrs.getOrElse(tt.crs),
          crs.getOrElse(tt.crs),
          cellSize.getOrElse(tt.cellSize),
          bandMaps
        )
      }
    }
  }
}
