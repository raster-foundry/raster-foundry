package com.azavea.rf.datamodel

sealed abstract class ThumbnailSize(val repr: String)

object ThumbnailSize {
  case object SMALL extends ThumbnailSize("SMALL")
  case object LARGE extends ThumbnailSize("LARGE")
  case object SQUARE extends ThumbnailSize("SQUARE")

  def fromString(s: String): ThumbnailSize = s match {
    case "SMALL" => SMALL
    case "LARGE" => LARGE
    case "SQUARE" => SQUARE
  }

  def values = Seq(SMALL, LARGE, SQUARE)
}

