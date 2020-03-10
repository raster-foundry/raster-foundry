package com.rasterfoundry.datamodel

import cats.syntax.either._
import io.circe._

sealed abstract class TileLayerType(val repr: String) {
  override def toString = repr
}

object TileLayerType {
  case object TMS extends TileLayerType("TMS")
  case object MVT extends TileLayerType("MVT")

  def fromString(s: String): TileLayerType = s.toUpperCase match {
    case "TMS" => TMS
    case "MVT" => MVT
    case _     => throw new Exception(s"Invalid string: $s")
  }

  implicit val annotationProjectTypeEncoder: Encoder[TileLayerType] =
    Encoder.encodeString.contramap[TileLayerType](_.toString)

  implicit val annotationProjectTypeDecoder: Decoder[TileLayerType] =
    Decoder.decodeString.emap { str =>
      Either
        .catchNonFatal(fromString(str))
        .leftMap(_ => "TileLayerType")
    }
}
