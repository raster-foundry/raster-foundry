package com.azavea.rf.datamodel

import io.circe._
import cats.syntax.either._

sealed abstract class JobStatus(val repr: String) {
  override def toString = repr
}

object JobStatus {
  case object Uploading extends JobStatus("UPLOADING")
  case object Success extends JobStatus("SUCCESS")
  case object Failure extends JobStatus("FAILURE")
  case object PartialFailure extends JobStatus("PARTIALFAILURE")
  case object Queued extends JobStatus("QUEUED")
  case object Processing extends JobStatus("PROCESSING")

  def fromString(s: String): JobStatus = s.toUpperCase match {
    case "UPLOADING"      => Uploading
    case "SUCCESS"        => Success
    case "FAILURE"        => Failure
    case "PARTIALFAILURE" => PartialFailure
    case "QUEUED"         => Queued
    case "PROCESSING"     => Processing
    case _                => throw new Exception(s"Invalid string: $s")
  }

  implicit val jobStatusEncoder: Encoder[JobStatus] =
    Encoder.encodeString.contramap[JobStatus](_.toString)

  implicit val jobStatusDecoder: Decoder[JobStatus] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(t => "JobStatus")
    }
}
