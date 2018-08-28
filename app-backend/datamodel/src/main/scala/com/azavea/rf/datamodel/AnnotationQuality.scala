package com.azavea.rf.datamodel

import io.circe._
import cats.syntax.either._

sealed abstract class AnnotationQuality(val repr: String) {
  override def toString = repr
}

object AnnotationQuality {
  case object Yes extends AnnotationQuality("YES")
  case object No extends AnnotationQuality("NO")
  case object Miss extends AnnotationQuality("MISS")
  case object Unsure extends AnnotationQuality("UNSURE")

  def fromString(s: String): AnnotationQuality = s.toUpperCase match {
    case "YES"    => Yes
    case "NO"     => No
    case "MISS"   => Miss
    case "UNSURE" => Unsure
    case _ =>
      throw new IllegalArgumentException(
        s"Argument $s cannot be mapped to AnnotationQuality")
  }

  implicit val annotationQualityEncoder: Encoder[AnnotationQuality] =
    Encoder.encodeString.contramap[AnnotationQuality](_.toString)

  implicit val annotationQualityDecoder: Decoder[AnnotationQuality] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(t => "AnnotationQuality")
    }
}
