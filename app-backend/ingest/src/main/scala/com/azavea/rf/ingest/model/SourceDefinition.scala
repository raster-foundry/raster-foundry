package com.azavea.rf.ingest.model

import java.net.URI

import spray.json._
import DefaultJsonProtocol._
import geotrellis.raster._
import geotrellis.vector._
import geotrellis.proj4.{CRS, LatLng}
import com.azavea.rf.ingest.util.getTiffTags
import com.typesafe.scalalogging.LazyLogging

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
  extentCrs: CRS,
  crs: CRS,
  cellSize: CellSize,
  bandMaps: Array[BandMapping]
)

object SourceDefinition extends LazyLogging {
  implicit val jsonFormat = jsonFormat6(SourceDefinition.apply _)

  /** This object handles deserialization of source definitions and overrides any values which
    *  might be found in the tiff's header.
    */
  case class Overrides(
    uri: URI,
    extent: Option[Extent],
    extentCrs: Option[CRS],
    crs: Option[CRS],
    cellSize: Option[CellSize],
    bandMaps: Array[BandMapping]
  ) {
    def toSourceDefinition: SourceDefinition = {
      if (extent.isDefined && crs.isDefined && cellSize.isDefined) {
        SourceDefinition(
          uri,
          extent.get,
          crs.get,
          extentCrs.getOrElse(LatLng),
          cellSize.get,
          bandMaps
        )
      } else {
        logger.debug(s"Reading tiff tags: $uri")
        lazy val tt = getTiffTags(uri)
        SourceDefinition(
          uri,
          extent.getOrElse(tt.extent),
          crs.getOrElse(tt.crs),
          extentCrs.getOrElse(tt.crs),
          cellSize.getOrElse(tt.cellSize),
          bandMaps
        )
      }
    }
  }

  object Overrides {
    implicit val jsonFormat = jsonFormat6(Overrides.apply _)
  }
}
