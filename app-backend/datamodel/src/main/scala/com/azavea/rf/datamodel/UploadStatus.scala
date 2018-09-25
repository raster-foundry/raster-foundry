package com.azavea.rf.datamodel

import io.circe._
import cats.syntax.either._

sealed abstract class UploadStatus(val repr: String) {
  override def toString = repr
}

object UploadStatus {
  case object Created extends UploadStatus("CREATED")
  case object Uploading extends UploadStatus("UPLOADING")
  case object Uploaded extends UploadStatus("UPLOADED")
  case object Queued extends UploadStatus("QUEUED")
  case object Processing extends UploadStatus("PROCESSING")
  case object Complete extends UploadStatus("COMPLETE")
  case object Failed extends UploadStatus("FAILED")
  case object Aborted extends UploadStatus("ABORTED")

  def fromString(s: String): UploadStatus = s.toUpperCase match {
    case "CREATED"    => Created
    case "UPLOADING"  => Uploading
    case "UPLOADED"   => Uploaded
    case "QUEUED"     => Queued
    case "PROCESSING" => Processing
    case "COMPLETE"   => Complete
    case "FAILED"     => Failed
    case "ABORTED"    => Aborted
    case _ =>
      throw new IllegalArgumentException(
        s"Argument $s cannot be mapped to UploadStatus")
  }

  implicit val uploadStatusEncoder: Encoder[UploadStatus] =
    Encoder.encodeString.contramap[UploadStatus](_.toString)

  implicit val uploadStatusDecoder: Decoder[UploadStatus] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(t => "UploadStatus")
    }
}
