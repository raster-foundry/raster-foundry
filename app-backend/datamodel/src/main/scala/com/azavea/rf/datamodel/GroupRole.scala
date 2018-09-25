package com.azavea.rf.datamodel

import io.circe._
import cats.syntax.either._

sealed abstract class GroupRole(val repr: String) {
  override def toString = repr
}

object GroupRole {
  case object Admin extends GroupRole("ADMIN")
  case object Member extends GroupRole("MEMBER")

  def fromString(s: String): GroupRole = s.toUpperCase match {
    case "ADMIN"  => Admin
    case "MEMBER" => Member
    case _        => throw new Exception(s"Invalid GroupRole: ${s}")
  }

  implicit val GroupRoleEncoder: Encoder[GroupRole] =
    Encoder.encodeString.contramap[GroupRole](_.toString)

  implicit val GroupRoleDecoder: Decoder[GroupRole] =
    Decoder.decodeString.emap { s =>
      Either.catchNonFatal(fromString(s)).leftMap(gt => "GroupRole")
    }
}
