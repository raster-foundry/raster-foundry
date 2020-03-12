package com.rasterfoundry.datamodel

import cats.implicits._
import io.circe._
import io.circe.syntax._

abstract class AnnotationProjectStatus(val repr: String)

abstract class ProgressStage(val stage: String)
    extends AnnotationProjectStatus(stage)
abstract class ErrorStage(val stage: String)
    extends AnnotationProjectStatus(stage)

object ErrorStage {
  val fromString = new PartialFunction[String, ErrorStage] {
    def apply(s: String) = s.toUpperCase match {
      case "TASK_GRID_FAILURE" => AnnotationProjectStatus.TaskGridFailure
      case "IMAGE_INGESTION_FAILURE" =>
        AnnotationProjectStatus.ImageIngestionFailure
      case "UNKNOWN_FAILURE" => AnnotationProjectStatus.UnknownFailure
    }
    def isDefinedAt(s: String): Boolean =
      Set("TASK_GRID_FAILURE", "IMAGE_INGESTION_FAILURE", "UNKNOWN_FAILURE")
        .contains(s.toUpperCase)
  }

}

object ProgressStage {
  val fromString = new PartialFunction[String, ProgressStage] {
    def apply(s: String) = s.toUpperCase match {
      case "WAITING"    => AnnotationProjectStatus.Waiting
      case "QUEUED"     => AnnotationProjectStatus.Queued
      case "PROCESSING" => AnnotationProjectStatus.Processing
      case "READY"      => AnnotationProjectStatus.Ready
    }
    def isDefinedAt(s: String): Boolean =
      Set("WAITING", "QUEUED", "PROCESSING", "READY").contains(s.toUpperCase)
  }
}

object AnnotationProjectStatus {
  case object Waiting extends ProgressStage("WAITING")
  case object Queued extends ProgressStage("QUEUED")
  case object Processing extends ProgressStage("PROCESSING")
  case object Ready extends ProgressStage("READY")

  case object TaskGridFailure extends ErrorStage("TASK_GRID_FAILURE")
  case object ImageIngestionFailure
      extends ErrorStage("IMAGE_INGESTION_FAILURE")
  case object UnknownFailure extends ErrorStage("UNKNOWN_FAILURE")

  implicit val decoderErrorStatus: Decoder[ErrorStage] =
    Decoder.forProduct1("errorStage")(
      (stage: String) => ErrorStage.fromString(stage)
    )
  implicit val encoderErrorStatus: Encoder[ErrorStage] =
    Encoder.forProduct1("errorStage")(
      (stage: ErrorStage) => stage.repr
    )
  implicit val decoderProgressStatus: Decoder[ProgressStage] =
    Decoder.forProduct1("progressStage")(
      (stage: String) => ProgressStage.fromString(stage)
    )
  implicit val encoderProgressStatus: Encoder[ProgressStage] =
    Encoder.forProduct1("progressStage")(
      (status: ProgressStage) => status.repr
    )

  def fromString: PartialFunction[String, AnnotationProjectStatus] =
    ErrorStage.fromString.orElse(ProgressStage.fromString)

  implicit def encAnnProjStat: Encoder[AnnotationProjectStatus] =
    new Encoder[AnnotationProjectStatus] {
      def apply(thing: AnnotationProjectStatus): Json = thing match {
        case ps: ProgressStage => Map("progressStage" -> ps.repr).asJson
        case es: ErrorStage    => Map("errorStage" -> es.repr).asJson
      }
    }

  implicit def decAnnProjStat: Decoder[AnnotationProjectStatus] =
    Decoder[ProgressStage].widen or Decoder[ErrorStage].widen
}
