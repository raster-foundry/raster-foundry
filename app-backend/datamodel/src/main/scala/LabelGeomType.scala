package com.rasterfoundry.datamodel

import cats.syntax.either._
import io.circe._

sealed abstract class LabelGeomType(val repr: String) {
  override def toString = repr
}

object LabelGeomType {
  case object PointLabel extends LabelGeomType("POINT")
  case object PolygonLabel extends LabelGeomType("POLYGON")

  def fromString(s: String): LabelGeomType = s.toUpperCase match {
    case "POINT"   => PointLabel
    case "POLYGON" => PolygonLabel
    case _         => throw new Exception(s"Invalid string: $s")
  }

  implicit val encLabelGeomType: Encoder[LabelGeomType] =
    Encoder.encodeString.contramap[LabelGeomType](_.toString)

  implicit val decLabelGeomType: Decoder[LabelGeomType] =
    Decoder.decodeString.emap { str =>
      Either
        .catchNonFatal(fromString(str))
        .leftMap(_ => "LabelGeomType")
    }
}
