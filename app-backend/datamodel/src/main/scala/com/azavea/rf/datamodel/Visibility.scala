package com.azavea.rf.datamodel

import spray.json._
import DefaultJsonProtocol._

sealed abstract class Visibility(val repr: String) {
  override def toString = repr
}

object Visibility {
  case object Public extends Visibility("PUBLIC")
  case object Organization extends Visibility("ORGANIZATION")
  case object Private extends Visibility("PRIVATE")

  def fromString(s: String): Visibility = s.toUpperCase match {
    case "PUBLIC" => Public
    case "ORGANIZATION" => Organization
    case "PRIVATE" => Private
  }

  implicit object DefaultVisibilityJsonFormat extends RootJsonFormat[Visibility] {
    def write(vis: Visibility): JsValue = JsString(vis.toString)
    def read(js: JsValue): Visibility = js match {
      case JsString(vis) => fromString(vis)
      case _ =>
        deserializationError("Failed to parse thumbnail size string representation (${js}) to ThumbnailSize")
    }
  }
}







