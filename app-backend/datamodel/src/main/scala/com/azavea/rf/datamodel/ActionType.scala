package com.azavea.rf.datamodel

import io.circe._
import cats.syntax.either._

sealed abstract class ActionType(val repr: String) {
  override def toString = repr
}

object ActionType {
  case object View extends ActionType("VIEW")
  case object Edit extends ActionType("EDIT")
  case object Deactivate extends ActionType("DEACTIVATE")
  case object Delete extends ActionType("DELETE")
  case object Annotate extends ActionType("ANNOTATE")
  case object Export extends ActionType("EXPORT")
  case object Download extends ActionType("DOWNLOAD")

  def fromString(s: String): ActionType = s.toUpperCase match {
    case "VIEW"       => View
    case "EDIT"       => Edit
    case "DEACTIVATE" => Deactivate
    case "DELETE"     => Delete
    case "ANNOTATE"   => Annotate
    case "EXPORT"     => Export
    case "DOWNLOAD"   => Download
    case _            => throw new Exception(s"Invalid ActionType: ${s}")
  }

  implicit val ActionTypeEncoder: Encoder[ActionType] =
    Encoder.encodeString.contramap[ActionType](_.toString)

  implicit val ActionTypeDecoder: Decoder[ActionType] =
    Decoder.decodeString.emap { s =>
      Either.catchNonFatal(fromString(s)).leftMap(gt => "ActionType")
    }
}
