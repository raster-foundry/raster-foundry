package com.rasterfoundry.common.datamodel

import io.circe.generic.JsonCodec

@JsonCodec
final case class BandOverride(
    redBand: Int,
    greenBand: Int,
    blueBand: Int
)

@JsonCodec
final case class ColorComposite(
    label: String,
    value: BandOverride
)
