package com.rasterfoundry.datamodel

import cats.syntax.either._
import io.circe._

sealed abstract class UserVisibility(val repr: String) {
  override def toString = repr
}

object UserVisibility {
  case object Public extends UserVisibility("PUBLIC")
  case object Private extends UserVisibility("PRIVATE")

  def fromString(s: String): UserVisibility = s.toUpperCase match {
    case "PUBLIC"  => Public
    case "PRIVATE" => Private
    case _         => throw new Exception(s"Invalid UserVisibility: ${s}")
  }

  implicit val UserVisibilityEncoder: Encoder[UserVisibility] =
    Encoder.encodeString.contramap[UserVisibility](_.toString)

  implicit val UserVisibilityDecoder: Decoder[UserVisibility] =
    Decoder.decodeString.emap { s =>
      Either.catchNonFatal(fromString(s)).leftMap(_ => "UserVisibility")
    }
}
