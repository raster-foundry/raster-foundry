package com.rasterfoundry.datamodel

import cats.syntax.either._
import io.circe._

sealed abstract class FileType(val repr: String) {
  override def toString = repr
}

object FileType {
  case object Geotiff extends FileType("GEOTIFF")
  case object GeotiffWithMetadata extends FileType("GEOTIFF_WITH_METADATA")
  case object NonSpatial extends FileType("NON_SPATIAL")
  case object GeoJson extends FileType("GEOJSON")

  def fromString(s: String): FileType = s.toUpperCase match {
    case "GEOTIFF"               => Geotiff
    case "GEOTIFF_WITH_METADATA" => GeotiffWithMetadata
    case "NON_SPATIAL"           => NonSpatial
    case "GEOJSON"               => GeoJson
  }

  implicit val fileTypeEncoder: Encoder[FileType] =
    Encoder.encodeString.contramap[FileType](_.toString)

  implicit val fileTypeDecoder: Decoder[FileType] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(_ => "FileType")
    }
}
