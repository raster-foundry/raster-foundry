package com.azavea.rf.datamodel

import io.circe._
import cats.syntax.either._

sealed abstract class FileType(val repr: String) {
  override def toString = repr
}

object FileType {
  case object Geotiff extends FileType("GEOTIFF")
  case object GeotiffWithMetadata extends FileType("GEOTIFF_WITH_METADATA")

  def fromString(s: String): FileType = s.toUpperCase match {
    case "GEOTIFF"               => Geotiff
    case "GEOTIFF_WITH_METADATA" => GeotiffWithMetadata
  }

  implicit val fileTypeEncoder: Encoder[FileType] =
    Encoder.encodeString.contramap[FileType](_.toString)

  implicit val fileTypeDecoder: Decoder[FileType] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(t => "FileType")
    }
}
