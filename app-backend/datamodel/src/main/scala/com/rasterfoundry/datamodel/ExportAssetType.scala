package com.rasterfoundry.datamodel

import cats.implicits._
import io.circe._

sealed abstract class ExportAssetType(val repr: String) {
  override def toString = repr
}

object ExportAssetType {
  case object SignedURL extends ExportAssetType("SIGNED_URL")
  case object COG extends ExportAssetType("COG")

  def fromString(s: String): Option[ExportAssetType] =
    Option(s.toUpperCase) collect {
      case "SIGNED_URL" => SignedURL
      case "COG"        => COG
    }

  def unsafeFromString(s: String): ExportAssetType =
    fromString(s).getOrElse(
      throw new Exception(s"Unsupported export asset type string: $s")
    )

  implicit val exportAssetTypeEncoder: Encoder[ExportAssetType] =
    Encoder.encodeString.contramap[ExportAssetType](_.toString)

  implicit val exportAssetTypeDecoder: Decoder[ExportAssetType] =
    Decoder.decodeString.emap { str =>
      Either
        .catchNonFatal(unsafeFromString(str))
        .leftMap(_ =>
          s"The string '$str' could not be decoded to ExportAssetType")
    }
}
