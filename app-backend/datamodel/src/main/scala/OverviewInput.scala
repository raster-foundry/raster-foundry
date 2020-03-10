package com.rasterfoundry.datamodel

import io.circe.generic.JsonCodec

import java.util.UUID

@JsonCodec
final case class OverviewInput(
    outputLocation: String,
    projectId: UUID,
    projectLayerId: UUID,
    refreshToken: String,
    minZoomLevel: Int
)
