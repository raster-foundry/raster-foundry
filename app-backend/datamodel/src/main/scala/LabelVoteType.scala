package com.rasterfoundry.datamodel

import cats.syntax.either._
import io.circe._

sealed abstract class LabelVoteType(val repr: String) {
  override def toString = repr
}

object LabelVoteType {
  case object Pass extends LabelVoteType("PASS")
  case object Fail extends LabelVoteType("FAIL")

  def fromString(s: String): LabelVoteType = s.toUpperCase match {
    case "PASS" => Pass
    case "FAIL" => Fail
    case _      => throw new Exception(s"Invalid string: $s")
  }

  implicit val labelVoteTypeEncoder: Encoder[LabelVoteType] =
    Encoder.encodeString.contramap[LabelVoteType](_.toString)

  implicit val labelVoteTypeDecoder: Decoder[LabelVoteType] =
    Decoder.decodeString.emap { str =>
      Either
        .catchNonFatal(fromString(str))
        .leftMap(_ => "LabelVoteType")
    }
}
