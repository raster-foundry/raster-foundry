package com.rasterfoundry.datamodel

import cats.syntax.either._
import io.circe._

sealed abstract class Visibility(val repr: String) {
  override def toString = repr
}

object Visibility {
  case object Public extends Visibility("PUBLIC")
  case object Organization extends Visibility("ORGANIZATION")
  case object Private extends Visibility("PRIVATE")

  def fromString(s: String): Visibility = s.toUpperCase match {
    case "PUBLIC"       => Public
    case "ORGANIZATION" => Organization
    case "PRIVATE"      => Private
  }

  implicit val visibilityEncoder: Encoder[Visibility] =
    Encoder.encodeString.contramap[Visibility](_.toString)

  implicit val visibilityDecoder: Decoder[Visibility] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(_ => "Visibility")
    }
}
