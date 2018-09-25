package com.azavea.rf.datamodel

import io.circe._
import cats.syntax.either._

sealed abstract class ExportType(val repr: String) {
  override def toString = repr
}

object ExportType {
  case object Dropbox extends ExportType("DROPBOX")
  case object S3 extends ExportType("S3")
  case object Local extends ExportType("LOCAL")

  def fromString(s: String): ExportType = s.toUpperCase match {
    case "DROPBOX" => Dropbox
    case "S3"      => S3
    case "LOCAL"   => Local
  }

  implicit val uploadTypeEncoder: Encoder[ExportType] =
    Encoder.encodeString.contramap[ExportType](_.toString)

  implicit val uploadTypeDecoder: Decoder[ExportType] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(t => "ExportType")
    }
}
