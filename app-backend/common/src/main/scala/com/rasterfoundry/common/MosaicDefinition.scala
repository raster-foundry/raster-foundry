package com.rasterfoundry.common

import java.util.UUID

import com.rasterfoundry.common.color.ColorCorrect
import com.rasterfoundry.datamodel._
import geotrellis.vector.MultiPolygon
import io.circe.Json
import io.circe.generic.JsonCodec

@JsonCodec
final case class MosaicDefinition(
    sceneId: UUID,
    projectId: UUID,
    colorCorrections: ColorCorrect.Params,
    sceneType: Option[SceneType] = None,
    ingestLocation: Option[String],
    footprint: Option[MultiPolygon],
    isSingleBand: Boolean,
    singleBandOptions: Option[Json],
    mask: Option[MultiPolygon],
    noDataValue: Option[Double]
)
