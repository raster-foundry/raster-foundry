package com.azavea.rf.datamodel

import io.circe._
import cats.syntax.either._

sealed abstract class SubjectType(val repr: String) {
  override def toString = repr
}

object SubjectType {
  case object All extends SubjectType("ALL")
  case object Platform extends SubjectType("PLATFORM")
  case object Organization extends SubjectType("ORGANIZATION")
  case object Team extends SubjectType("TEAM")
  case object User extends SubjectType("USER")

  def fromString(s: String): SubjectType = s.toUpperCase match {
    case "ALL"          => All
    case "PLATFORM"     => Platform
    case "ORGANIZATION" => Organization
    case "TEAM"         => Team
    case "USER"         => User
    case _              => throw new Exception(s"Invalid SubjectType: ${s}")
  }

  implicit val SubjectTypeEncoder: Encoder[SubjectType] =
    Encoder.encodeString.contramap[SubjectType](_.toString)

  implicit val SubjectTypeDecoder: Decoder[SubjectType] =
    Decoder.decodeString.emap { s =>
      Either.catchNonFatal(fromString(s)).leftMap(gt => "SubjectType")
    }
}
