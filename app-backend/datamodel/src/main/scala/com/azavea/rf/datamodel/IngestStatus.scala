package com.azavea.rf.datamodel

import io.circe._
import cats.syntax.either._

import java.security.InvalidParameterException

sealed abstract class IngestStatus(val repr: String) {
  override def toString = repr

  /** Order possible statuses to support ingest workflow
    *
    * These values are used to filter to scenes with IngestStatuses
    * no greater than a specified value, so for example, scenes that
    * have either failed to be ingested or haven't been attempted bt
    * that aren't queued or currently ingesting.
    */
  def toInt: Int = repr.toUpperCase match {
    case "NOTINGESTED"  => 1
    case "QUEUED"       => 2
    case "TOBEINGESTED" => 3
    case "INGESTING"    => 4
    case "INGESTED"     => 5
    case "FAILED"       => 0
  }
}

object IngestStatus {
  case object NotIngested extends IngestStatus("NOTINGESTED")
  case object Queued extends IngestStatus("QUEUED")
  case object ToBeIngested extends IngestStatus("TOBEINGESTED")
  case object Ingesting extends IngestStatus("INGESTING")
  case object Ingested extends IngestStatus("INGESTED")
  case object Failed extends IngestStatus("FAILED")

  def fromString(s: String): IngestStatus = s.toUpperCase match {
    case "NOTINGESTED"  => NotIngested
    case "QUEUED"       => Queued
    case "TOBEINGESTED" => ToBeIngested
    case "INGESTING"    => Ingesting
    case "INGESTED"     => Ingested
    case "FAILED"       => Failed
    case _              => throw new InvalidParameterException(s"Invalid IngestStatus: $s")
  }

  implicit val ingestStatusEncoder: Encoder[IngestStatus] =
    Encoder.encodeString.contramap[IngestStatus](_.toString)

  implicit val ingestStatusDecoder: Decoder[IngestStatus] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(t => "IngestStatus")
    }
}
