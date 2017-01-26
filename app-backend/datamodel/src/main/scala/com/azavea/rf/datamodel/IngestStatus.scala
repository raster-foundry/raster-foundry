package com.azavea.rf.datamodel

import spray.json._
import DefaultJsonProtocol._

sealed abstract class IngestStatus(val repr: String) {
  override def toString = repr
}

object IngestStatus {
  case object NotIngested extends IngestStatus("NOTINGESTED")
  case object ToBeIngested extends IngestStatus("TOBEINGESTED")
  case object Ingesting extends IngestStatus("INGESTING")
  case object Ingested extends IngestStatus("INGESTED")
  case object Failed extends IngestStatus("FAILED")

  def fromString(s: String): IngestStatus = s.toUpperCase match {
    case "NOTINGESTED" => NotIngested
    case "TOBEINGESTED" => ToBeIngested
    case "INGESTING" => Ingesting
    case "INGESTED" => Ingested
    case "FAILED" => Failed
    case _ => throw new Exception(s"Invalid string: $s")
  }

  implicit object DefaultIngestStatusJsonFormat extends RootJsonFormat[IngestStatus] {
    def write(status: IngestStatus): JsValue = JsString(status.toString)
    def read(js: JsValue): IngestStatus = js match {
      case JsString(status) => fromString(status)
      case _ =>
        deserializationError("Failed to parse ingest string representation (${js}) to IngestStatus")
    }
  }
}
