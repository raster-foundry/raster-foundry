package com.rasterfoundry.lambda.overviews
import java.util.UUID

import io.circe.Decoder

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
}
