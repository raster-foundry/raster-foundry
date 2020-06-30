package com.rasterfoundry.datamodel

import cats.implicits._
import io.circe._

sealed abstract class TaskType(val repr: String) {
  override def toString = repr
}

object TaskType {
  case object Label extends TaskType("LABEL")
  case object Review extends TaskType("REVIEW")

  def fromString(s: String): TaskType = s.toUpperCase match {
    case "LABEL"  => Label
    case "REVIEW" => Review
  }

  implicit val taskTypeEncoder: Encoder[TaskType] =
    Encoder.encodeString.contramap[TaskType](_.toString)

  implicit val taskTypeDecoder: Decoder[TaskType] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(_ => "TaskType")
    }

  implicit val taskTypeKeyEncoder: KeyEncoder[TaskType] =
    new KeyEncoder[TaskType] {
      override def apply(taskType: TaskType): String = taskType.toString
    }
}
