package com.rasterfoundry.datamodel

import java.util.UUID
import io.circe.{Decoder, Encoder}

final case class OverviewInput(
    outputLocation: String,
    projectId: UUID,
    projectLayerId: UUID,
    refreshToken: String,
    pixelSizeMeters: Int
)

object OverviewInput {

  implicit val decodeOverview: Decoder[OverviewInput] =
    Decoder.forProduct5("outputLocation",
                        "projectId",
                        "projectLayerId",
                        "refreshToken",
                        "pixelSizeMeters") {
      OverviewInput.apply _
    }

  implicit val encodeOverview: Encoder[OverviewInput] =
  Encoder.forProduct5("outputLocation",
                      "projectId",
                      "projectLayerId",
                      "refreshToken",
                      "pixelSizeMeters")(overviewInput =>
    (overviewInput.outputLocation, overviewInput.projectId, overviewInput.projectLayerId, overviewInput.refreshToken, overviewInput.pixelSizeMeters)
  )
}
