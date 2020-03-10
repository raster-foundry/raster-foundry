package com.rasterfoundry.common

import com.rasterfoundry.common.color.ColorCorrect
import com.rasterfoundry.datamodel._

import geotrellis.vector.MultiPolygon
import io.circe.Json
import io.circe.generic.JsonCodec

import java.util.UUID

@JsonCodec
final case class MosaicDefinition(
    sceneId: UUID,
    projectId: UUID,
    datasource: UUID,
    sceneName: String,
    colorCorrections: ColorCorrect.Params,
    sceneType: Option[SceneType] = None,
    ingestLocation: Option[String],
    footprint: Option[MultiPolygon],
    isSingleBand: Boolean,
    singleBandOptions: Option[Json],
    mask: Option[MultiPolygon],
    sceneMetadataFields: SceneMetadataFields,
    metadataFiles: List[String]
)
