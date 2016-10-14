package com.azavea.rf.datamodel

import spray.json._
import DefaultJsonProtocol._

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
    case "UPLOADING" => Uploading
    case "SUCCESS" => Success
    case "FAILURE" => Failure
    case "PARTIALFAILURE" => PartialFailure
    case "QUEUED" => Queued
    case "PROCESSING" => Processing
    case _ => throw new Exception(s"Invalid string: $s")
  }

  implicit object DefaultJobStatusJsonFormat extends RootJsonFormat[JobStatus] {
    def write(status: JobStatus): JsValue = JsString(status.toString)
    def read(js: JsValue): JobStatus = js match {
      case JsString(status) => fromString(status)
      case _ =>
        deserializationError("Failed to parse thumbnail size string representation (${js}) to JobStatus")
    }
  }
}


