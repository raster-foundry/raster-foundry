package com.azavea.rf.datamodel

import io.circe.{Decoder, Encoder}
import cats.syntax.either._

/** The possible sizes of thumbnail */
sealed abstract class ThumbnailSize(val repr: String) {
  override def toString = repr
}

object ThumbnailSize {
  implicit val thumbnailSizeEncoder: Encoder[ThumbnailSize] =
    Encoder.encodeString.contramap[ThumbnailSize](_.toString)
  implicit val thumbnailSizeDecoder: Decoder[ThumbnailSize] =
    Decoder.decodeString.emap { str =>
      Either
        .catchNonFatal(ThumbnailSize.fromString(str))
        .leftMap(_ => "ThumbnailSize")
    }

  case object Small extends ThumbnailSize("SMALL")
  case object Large extends ThumbnailSize("LARGE")
  case object Square extends ThumbnailSize("SQUARE")

  def fromString(s: String): ThumbnailSize = s.toUpperCase match {
    case "SMALL"  => Small
    case "LARGE"  => Large
    case "SQUARE" => Square
  }
}
