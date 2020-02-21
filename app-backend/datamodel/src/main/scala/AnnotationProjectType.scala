package com.rasterfoundry.datamodel

import cats.syntax.either._
import io.circe._

sealed abstract class AnnotationProjectType(val repr: String) {
  override def toString = repr
}

object AnnotationProjectType {
  case object Detection extends AnnotationProjectType("DETECTION")
  case object Classification extends AnnotationProjectType("CLASSIFICATION")
  case object Segmentation extends AnnotationProjectType("SEGMENTATION")

  def fromString(s: String): AnnotationProjectType = s.toUpperCase match {
    case "DETECTION"      => Detection
    case "CLASSIFICATION" => Classification
    case "SEGMENTATION"   => Segmentation
    case _                => throw new Exception(s"Invalid string: $s")
  }

  implicit val annotationProjectTypeEncoder: Encoder[AnnotationProjectType] =
    Encoder.encodeString.contramap[AnnotationProjectType](_.toString)

  implicit val annotationProjectTypeDecoder: Decoder[AnnotationProjectType] =
    Decoder.decodeString.emap { str =>
      Either
        .catchNonFatal(fromString(str))
        .leftMap(_ => "AnnotationProjectType")
    }
}
