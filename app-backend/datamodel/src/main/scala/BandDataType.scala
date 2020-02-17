package com.rasterfoundry.datamodel

import cats.syntax.either._
import io.circe._

sealed abstract class BandDataType(val repr: String) {
  override def toString = repr
}

object BandDataType {
  case object Diverging extends BandDataType("DIVERGING")
  case object Sequential extends BandDataType("SEQUENTIAL")
  case object Categorical extends BandDataType("CATEGORICAL")

  def fromString(s: String): BandDataType = s.toUpperCase match {
    case "DIVERGING"   => Diverging
    case "SEQUENTIAL"  => Sequential
    case "CATEGORICAL" => Categorical
  }

  implicit val bandDataTypeEncoder: Encoder[BandDataType] =
    Encoder.encodeString.contramap[BandDataType](_.toString)

  implicit val bandDataTypeDecoder: Decoder[BandDataType] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(_ => "BandDataType")
    }
}
