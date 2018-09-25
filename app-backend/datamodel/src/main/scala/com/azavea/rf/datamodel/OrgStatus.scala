package com.azavea.rf.datamodel

import io.circe._
import cats.syntax.either._

import java.security.InvalidParameterException

sealed abstract class OrgStatus(val repr: String) {
  override def toString = repr

  def toInt: Int = repr.toUpperCase match {
    case "INACTIVE"  => 0
    case "REQUESTED" => 1
    case "ACTIVE"    => 2
  }
}

object OrgStatus {
  case object Inactive extends OrgStatus("INACTIVE")
  case object Requested extends OrgStatus("REQUESTED")
  case object Active extends OrgStatus("ACTIVE")

  def fromString(s: String): OrgStatus = s.toUpperCase match {
    case "INACTIVE"  => Inactive
    case "REQUESTED" => Requested
    case "ACTIVE"    => Active
    case _           => throw new InvalidParameterException(s"Invalid OrgStatus: $s")
  }

  implicit val orgStatusEncoder: Encoder[OrgStatus] =
    Encoder.encodeString.contramap[OrgStatus](_.toString)

  implicit val orgStatusDecoder: Decoder[OrgStatus] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(t => "OrgStatus")
    }
}
