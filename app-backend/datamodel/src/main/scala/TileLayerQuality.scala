package com.rasterfoundry.datamodel

import cats.implicits._
import io.circe._

sealed abstract class TileLayerQuality(val repr: String) {
  override def toString = repr
}

object TileLayerQuality {
  case object Good extends TileLayerQuality("GOOD")
  case object Better extends TileLayerQuality("BETTER")
  case object Best extends TileLayerQuality("BEST")

  def fromString(s: String): TileLayerQuality = s.toUpperCase match {
    case "GOOD"   => Good
    case "BETTER" => Better
    case "BEST"   => Best
    case _        => throw new Exception(s"Invalid TileLayerQuality: ${s}")
  }

  implicit val encTileLayerQuality: Encoder[TileLayerQuality] =
    Encoder.encodeString.contramap[TileLayerQuality](_.toString)

  implicit val decTileLayerQuality: Decoder[TileLayerQuality] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(_ => "TileLayerQuality")
    }

}
