package com.rasterfoundry.datamodel

import cats.syntax.either._
import io.circe._

sealed abstract class TaskReviewStatus(val repr: String) {
  override def toString = repr
}

object TaskReviewStatus {
  case object ReviewPending extends TaskReviewStatus("REVIEW_PENDING")
  case object ReviewValidated extends TaskReviewStatus("REVIEW_VALIDATED")
  case object ReviewNeedsAttention
      extends TaskReviewStatus("REVIEW_NEEDS_ATTENTION")

  def fromString(s: String): TaskReviewStatus = s.toUpperCase match {
    case "REVIEW_PENDING"         => ReviewPending
    case "REVIEW_VALIDATED"       => ReviewValidated
    case "REVIEW_NEEDS_ATTENTION" => ReviewNeedsAttention
    case _                        => throw new Exception(s"Invalid string: $s")
  }

  implicit val taskReviewStatusEncoder: Encoder[TaskReviewStatus] =
    Encoder.encodeString.contramap[TaskReviewStatus](_.toString)

  implicit val taskReviewStatusDecoder: Decoder[TaskReviewStatus] =
    Decoder.decodeString.emap { str =>
      Either
        .catchNonFatal(fromString(str))
        .leftMap(_ => "TaskReviewStatus")
    }
}
