package com.rasterfoundry.datamodel

import cats.implicits._
import io.circe._

sealed abstract class TaskSessionType(val repr: String) {
  override def toString = repr
}

object TaskSessionType {
  case object LabelSession extends TaskSessionType("LABEL_SESSION")
  case object ValidateSession extends TaskSessionType("VALIDATE_SESSION")

  def fromString(s: String): TaskSessionType =
    s.toUpperCase match {
      case "LABEL_SESSION"    => LabelSession
      case "VALIDATE_SESSION" => ValidateSession
    }

  implicit val taskSessionEncoder: Encoder[TaskSessionType] =
    Encoder.encodeString.contramap[TaskSessionType](_.toString)

  implicit val taskSessionDecoder: Decoder[TaskSessionType] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(_ => "TaskSessionType")
    }
}
