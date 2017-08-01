package com.azavea.rf.datamodel

import io.circe._
import cats.syntax.either._

sealed abstract class BlendMode(val repr: String) {
  override def toString = repr
}

object BlendMode {
  case object Continuous extends BlendMode("CONTINUOUS")
  case object Discrete extends BlendMode("DISCRETE")
  case object None extends BlendMode("NONE")

  def fromString(s: String): BlendMode = s.toUpperCase match {
    case "CONTINUOUS" => Continuous
    case "DISCRETE" => Discrete
    case "NONE" => None
  }

  implicit val blendModeEncoder: Encoder[BlendMode] =
    Encoder.encodeString.contramap[BlendMode](_.toString)

  implicit val blendModeDecoder: Decoder[BlendMode] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(t => "BlendMode")
    }
}
