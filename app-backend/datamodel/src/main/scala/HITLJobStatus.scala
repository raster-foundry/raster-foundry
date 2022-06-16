package com.rasterfoundry.datamodel

import cats.syntax.either._
import io.circe._

sealed abstract class HITLJobStatus(val repr: String) {
  override def toString = repr
}

object HITLJobStatus {
  case object NotRun extends HITLJobStatus("NOTRUN")
  case object ToRun extends HITLJobStatus("TORUN")
  case object Running extends HITLJobStatus("RUNNING")
  case object Ran extends HITLJobStatus("RAN")
  case object Failed extends HITLJobStatus("FAILED")

  def fromString(s: String): HITLJobStatus =
    s.toUpperCase match {
      case "NOTRUN"  => NotRun
      case "TORUN"   => ToRun
      case "RUNNING" => Running
      case "RAN"     => Ran
      case "FAILED"  => Failed
    }

  implicit val hitlJobStatusEncoder: Encoder[HITLJobStatus] =
    Encoder.encodeString.contramap[HITLJobStatus](_.toString)

  implicit val hitlJobStatusDecoder: Decoder[HITLJobStatus] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(_ => "HITLJobStatus")
    }
}
