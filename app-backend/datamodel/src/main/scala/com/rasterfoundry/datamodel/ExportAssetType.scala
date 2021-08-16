package com.rasterfoundry.datamodel

import cats.implicits._
import io.circe._

sealed abstract class ExportAssetType(val repr: String) {
  override def toString = repr
}

object ExportAssetType {
  case object TileLayer extends ExportAssetType("TILE_LAYER")
  case object SignedURL extends ExportAssetType("SIGNED_URL")
  case object Images extends ExportAssetType("IMAGES")

  def fromString(s: String): ExportAssetType =
    s.toUpperCase match {
      case "TILE_LAYER" => TileLayer
      case "SIGNED_URL" => SignedURL
      case "IMAGES"     => Images
    }

  implicit val exportAssetTypeEncoder: Encoder[ExportAssetType] =
    Encoder.encodeString.contramap[ExportAssetType](_.toString)

  implicit val exportAssetTypeDecoder: Decoder[ExportAssetType] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(_ => "ExportAssetType")
    }
}
