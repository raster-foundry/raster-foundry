package com.azavea.rf.datamodel

import io.circe._
import cats.syntax.either._

sealed abstract class UploadType(val repr: String) {
  override def toString = repr
}

object UploadType {
  case object Dropbox extends UploadType("DROPBOX")
  case object S3 extends UploadType("S3")
  case object Local extends UploadType("LOCAL")
  case object Planet extends UploadType("PLANET")

  def fromString(s: String): UploadType = s.toUpperCase match {
    case "DROPBOX" => Dropbox
    case "S3" => S3
    case "LOCAL" => Local
    case "PLANET" => Planet
  }

  implicit val uploadTypeEncoder: Encoder[UploadType] =
    Encoder.encodeString.contramap[UploadType](_.toString)

  implicit val uploadTypeDecoder: Decoder[UploadType] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(t => "UploadType")
    }
}
