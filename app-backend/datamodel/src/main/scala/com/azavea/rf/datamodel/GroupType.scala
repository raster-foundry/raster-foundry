package com.azavea.rf.datamodel

import io.circe._
import cats.syntax.either._

import java.util.UUID

sealed abstract class GroupType(val repr: String) {
  override def toString = repr
}

object GroupType {
  case object Platform extends GroupType("PLATFORM")
  case object Organization extends GroupType("ORGANIZATION")
  case object Team extends GroupType("TEAM")

  def fromString(s: String): GroupType = s.toUpperCase match {
    case "PLATFORM"     => Platform
    case "ORGANIZATION" => Organization
    case "TEAM"         => Team
    case _              => throw new Exception(s"Invalid GroupType: ${s}")
  }

  implicit val groupTypeEncoder: Encoder[GroupType] =
    Encoder.encodeString.contramap[GroupType](_.toString)

  implicit val groupTypeDecoder: Decoder[GroupType] =
    Decoder.decodeString.emap { s =>
      Either.catchNonFatal(fromString(s)).leftMap(gt => "GroupType")
    }
}
