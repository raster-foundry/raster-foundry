package com.rasterfoundry.datamodel

import eu.timepit.refined.types.string.NonEmptyString
import io.circe._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.refined._

import java.sql.Timestamp
import java.util.UUID

final case class TaskSession(
    id: UUID,
    createdAt: Timestamp,
    lastTickAt: Timestamp,
    completedAt: Option[Timestamp],
    fromStatus: TaskStatus,
    toStatus: Option[TaskStatus],
    sessionType: TaskSessionType,
    userId: String,
    taskId: UUID,
    note: Option[String],
    previousSessionId: Option[UUID]
)

object TaskSession {
  implicit val encTaskSession: Encoder[TaskSession] = deriveEncoder
  implicit val decTaskSession: Decoder[TaskSession] = deriveDecoder

  final case class Create(
      sessionType: TaskSessionType
  )

  object Create {
    implicit val decCreate: Decoder[Create] = deriveDecoder
    implicit val encCreate: Encoder[Create] = deriveEncoder
  }

  final case class Complete(
      toStatus: TaskStatus,
      note: Option[NonEmptyString]
  )

  object Complete {
    implicit val decComplete: Decoder[Complete] = deriveDecoder
    implicit val encComplete: Encoder[Complete] = deriveEncoder
  }

}
