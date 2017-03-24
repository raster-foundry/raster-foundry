package com.azavea.rf.datamodel


/** The possible sizes of thumbnail */
sealed abstract class ThumbnailSize(val repr: String) {
  override def toString = repr
}

object ThumbnailSize {
  case object Small extends ThumbnailSize("SMALL")
  case object Large extends ThumbnailSize("LARGE")
  case object Square extends ThumbnailSize("SQUARE")

  def fromString(s: String): ThumbnailSize = s.toUpperCase match {
    case "SMALL" => Small
    case "LARGE" => Large
    case "SQUARE" => Square
  }
}
