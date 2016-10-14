package com.azavea.rf.datamodel

import spray.json._
import DefaultJsonProtocol._

/** The possible sizes of thumbnail */
sealed abstract class ThumbnailSize(val repr: String) {
  override def toString = repr
}

object ThumbnailSize {
  case object Small extends ThumbnailSize("SMALL")
  case object Large extends ThumbnailSize("LARGE")
  case object Square extends ThumbnailSize("SQUARE")

  implicit object DefaultThumbnailSizeJsonFormat extends RootJsonFormat[ThumbnailSize] {
    def write(size: ThumbnailSize): JsValue = JsString(size.toString)
    def read(js: JsValue): ThumbnailSize = js match {
      case JsString(dim) => fromString(dim)
      case _ =>
        deserializationError("Failed to parse thumbnail size string representation (${js}) to ThumbnailSize")
    }
  }

  def fromString(s: String): ThumbnailSize = s.toUpperCase match {
    case "SMALL" => Small
    case "LARGE" => Large
    case "SQUARE" => Square
  }
}
