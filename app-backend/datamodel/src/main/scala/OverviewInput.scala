package com.rasterfoundry.datamodel

import java.util.UUID
import io.circe.generic.JsonCodec

@JsonCodec
final case class OverviewInput(
    outputLocation: String,
    projectId: UUID,
    projectLayerId: UUID,
    refreshToken: String,
    minZoomLevel: Int
)
