package com.rasterfoundry.datamodel

import cats.syntax.either._
import io.circe._

sealed abstract class MLProjectType(val repr: String) {
  override def toString = repr
}

object MLProjectType {
  case object ObjectDetection extends MLProjectType("DETECTION")
  case object ChipClassification extends MLProjectType("CLASSIFICATION")
  case object SemanticSegmentation extends MLProjectType("SEGMENTATION")

  def fromString(s: String): MLProjectType = s.toUpperCase match {
    case "DETECTION"      => ObjectDetection
    case "CLASSIFICATION" => ChipClassification
    case "SEGMENTATION"   => SemanticSegmentation
    case _ =>
      throw new Exception(s"Unsupported Machine Learning Project Type: ${s}")
  }

  implicit val MLProjectTypeEncoder: Encoder[MLProjectType] =
    Encoder.encodeString.contramap[MLProjectType](_.toString)

  implicit val MLProjectTypeDecoder: Decoder[MLProjectType] =
    Decoder.decodeString.emap { s =>
      Either.catchNonFatal(fromString(s)).leftMap(_ => "MLProjectType")
    }
}
