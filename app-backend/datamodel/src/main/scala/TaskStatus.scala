package com.rasterfoundry.datamodel

import cats.implicits._
import io.circe._

sealed abstract class TaskStatus(val repr: String) {
  override def toString = repr
}

object TaskStatus {
  case object Unlabeled extends TaskStatus("UNLABELED")
  case object LabelingInProgress extends TaskStatus("LABELING_IN_PROGRESS")
  case object Labeled extends TaskStatus("LABELED")
  case object ValidationInProgress extends TaskStatus("VALIDATION_IN_PROGRESS")
  case object Validated extends TaskStatus("VALIDATED")
  case object Flagged extends TaskStatus("FLAGGED")
  case object Invalid extends TaskStatus("INVALID")

  def fromString(s: String): TaskStatus = s.toUpperCase match {
    case "UNLABELED"              => Unlabeled
    case "LABELING_IN_PROGRESS"   => LabelingInProgress
    case "LABELED"                => Labeled
    case "VALIDATION_IN_PROGRESS" => ValidationInProgress
    case "VALIDATED"              => Validated
    case "FLAGGED"                => Flagged
    case "INVALID"                => Invalid
  }

  implicit val taskStatusEncoder: Encoder[TaskStatus] =
    Encoder.encodeString.contramap[TaskStatus](_.toString)

  implicit val taskStatusDecoder: Decoder[TaskStatus] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(_ => "TaskStatus")
    }

  implicit val taskStatusKeyEncoder: KeyEncoder[TaskStatus] =
    new KeyEncoder[TaskStatus] {
      override def apply(taskStatus: TaskStatus): String = taskStatus.toString
    }
}
