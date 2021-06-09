package com.rasterfoundry.datamodel

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder

import scala.util.Try

final case class CampaignPerformance(
    avatarUri: String,
    name: String,
    tasksComplete: Long,
    hoursSpent: Double
)

object CampaignPerformance {
  implicit val decCampaignPerformance: Decoder[CampaignPerformance] =
    deriveDecoder

  // make encoding responsible for the average calculation to save the
  // database one aggregation and to make it impossible to mess up the
  // calculation in a way that creates an inconsistent result
  implicit val encCampaignPerformance: Encoder[CampaignPerformance] =
    Encoder.forProduct5(
      "imageUri",
      "name",
      "tasksComplete",
      "hoursSpent",
      "averageTasksPerHour"
    )(
      perf =>
        (
          perf.avatarUri,
          perf.name,
          perf.tasksComplete,
          perf.hoursSpent,
          Try(perf.tasksComplete / perf.hoursSpent).toOption
      ))
}
