package com.rasterfoundry.datamodel

import cats.syntax.either._
import io.circe._

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
  case object Platform extends ObjectType("PLATFORM")
  case object Organization extends ObjectType("ORGANIZATION")
  case object Team extends ObjectType("TEAM")
  case object User extends ObjectType("USER")
  case object Upload extends ObjectType("UPLOAD")
  case object Export extends ObjectType("EXPORT")
  case object Feed extends ObjectType("FEED")
  case object MapToken extends ObjectType("MAPTOKEN")
  case object License extends ObjectType("LICENSE")
  case object ToolTag extends ObjectType("TOOLTAG")
  case object ToolCategory extends ObjectType("TOOLCATEGORY")
  case object AnnotationProject extends ObjectType("ANNOTATIONPROJECT")
  case object Campaign extends ObjectType("CAMPAIGN")

  def fromString(s: String): ObjectType = s.toUpperCase match {
    case "PROJECT"           => Project
    case "SCENE"             => Scene
    case "DATASOURCE"        => Datasource
    case "SHAPE"             => Shape
    case "WORKSPACE"         => Workspace
    case "TEMPLATE"          => Template
    case "ANALYSIS"          => Analysis
    case "PLATFORM"          => Platform
    case "ORGANIZATION"      => Organization
    case "TEAM"              => Team
    case "USER"              => User
    case "UPLOAD"            => Upload
    case "EXPORT"            => Export
    case "FEED"              => Feed
    case "MAPTOKEN"          => MapToken
    case "LICENSE"           => License
    case "TOOLTAG"           => ToolTag
    case "TOOLCATEGORY"      => ToolCategory
    case "ANNOTATIONPROJECT" => AnnotationProject
    case "CAMPAIGN"          => Campaign
    case _                   => throw new Exception(s"Invalid ObjectType: ${s}")
  }

  implicit val ObjectTypeEncoder: Encoder[ObjectType] =
    Encoder.encodeString.contramap[ObjectType](_.toString)

  implicit val ObjectTypeDecoder: Decoder[ObjectType] =
    Decoder.decodeString.emap { s =>
      Either.catchNonFatal(fromString(s)).leftMap(_ => "ObjectType")
    }

  implicit val objectTypeKeyDecoder: KeyDecoder[ObjectType] =
    new KeyDecoder[ObjectType] {
      override def apply(key: String): Option[ObjectType] =
        Some(ObjectType.fromString(key))
    }

  implicit val objectTypeKeyEncoder: KeyEncoder[ObjectType] =
    new KeyEncoder[ObjectType] {
      override def apply(objectType: ObjectType): String = objectType.toString
    }
}
