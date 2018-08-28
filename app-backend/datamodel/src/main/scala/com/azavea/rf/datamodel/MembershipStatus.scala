package com.azavea.rf.datamodel

import io.circe._
import cats.syntax.either._

import java.security.InvalidParameterException

sealed abstract class MembershipStatus(val repr: String) {
  override def toString = repr
}

object MembershipStatus {
  case object Requested extends MembershipStatus("REQUESTED")
  case object Invited extends MembershipStatus("INVITED")
  case object Approved extends MembershipStatus("APPROVED")

  def fromString(s: String): MembershipStatus = s.toUpperCase match {
    case "REQUESTED" => Requested
    case "INVITED"   => Invited
    case "APPROVED"  => Approved
    case _ =>
      throw new InvalidParameterException(s"Invalid membership status: $s")
  }

  implicit val membershipStatusEncoder: Encoder[MembershipStatus] =
    Encoder.encodeString.contramap[MembershipStatus](_.toString)

  implicit val membershipStatusDecoder: Decoder[MembershipStatus] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(t => "MembershipStatus")
    }
}
