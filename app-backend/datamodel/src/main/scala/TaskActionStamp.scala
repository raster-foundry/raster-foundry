package com.rasterfoundry.datamodel

import io.circe._
import io.circe.generic.semiauto._

import java.time.Instant

case class TaskActionStamp(
    userId: String,
    timestamp: Instant,
    fromStatus: TaskStatus,
    toStatus: TaskStatus
)

object TaskActionStamp {
  implicit val encTaskActionStamp: Encoder[TaskActionStamp] = deriveEncoder
  implicit val decTaskActionStamp: Decoder[TaskActionStamp] = deriveDecoder
}
