package com.azavea.rf.datamodel

import io.circe._
import cats.syntax.either._

sealed abstract class ObjectType(val repr: String) {
  override def toString = repr
}

object ObjectType {
  case object Project extends ObjectType("PROJECT")
  case object Scene extends ObjectType("SCENE")
  case object Datasource extends ObjectType("DATASOURCE")
  case object Shape extends ObjectType("SHAPE")
  case object Workspace extends ObjectType("WORKSPACE")
  case object Template extends ObjectType("TEMPLATE")
  case object Analysis extends ObjectType("ANALYSIS")

  def fromString(s: String): ObjectType = s.toUpperCase match {
    case "PROJECT"    => Project
    case "SCENE"      => Scene
    case "DATASOURCE" => Datasource
    case "SHAPE"      => Shape
    case "WORKSPACE"  => Workspace
    case "TEMPLATE"   => Template
    case "ANALYSIS"   => Analysis
    case _            => throw new Exception(s"Invalid ObjectType: ${s}")
  }

  implicit val ObjectTypeEncoder: Encoder[ObjectType] =
    Encoder.encodeString.contramap[ObjectType](_.toString)

  implicit val ObjectTypeDecoder: Decoder[ObjectType] =
    Decoder.decodeString.emap { s =>
      Either.catchNonFatal(fromString(s)).leftMap(gt => "ObjectType")
    }
}
