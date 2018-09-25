package com.azavea.rf.datamodel

import io.circe._
import cats.syntax.either._

import java.security.InvalidParameterException

sealed abstract class ExportStatus(val repr: String) {
  override def toString = repr

  /** Order possible statuses to support ingest workflow
    *
    * These values are used to filter to scenes with ExportStatuses
    * no greater than a specified value, so for example, scenes that
    * have either failed to be ingested or haven't been attempted bt
    * that aren't queued or currently ingesting.
    */
  def toInt: Int = repr.toUpperCase match {
    case "NOTEXPORTED"  => 1
    case "TOBEEXPORTED" => 2
    case "EXPORTING"    => 3
    case "EXPORTED"     => 4
    case "FAILED"       => 0
  }
}

object ExportStatus {
  case object NotExported extends ExportStatus("NOTEXPORTED")
  case object ToBeExported extends ExportStatus("TOBEEXPORTED")
  case object Exporting extends ExportStatus("EXPORTING")
  case object Exported extends ExportStatus("EXPORTED")
  case object Failed extends ExportStatus("FAILED")

  def fromString(s: String): ExportStatus = s.toUpperCase match {
    case "NOTEXPORTED"  => NotExported
    case "TOBEEXPORTED" => ToBeExported
    case "EXPORTING"    => Exporting
    case "EXPORTED"     => Exported
    case "FAILED"       => Failed
    case _              => throw new InvalidParameterException(s"Invalid ExportStatus: $s")
  }

  implicit val ingestStatusEncoder: Encoder[ExportStatus] =
    Encoder.encodeString.contramap[ExportStatus](_.toString)

  implicit val ingestStatusDecoder: Decoder[ExportStatus] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(t => "ExportStatus")
    }
}
