package com.azavea.rf.datamodel

import java.net.URI
import java.util.UUID

import io.circe.generic.JsonCodec

/**
  * @param layerId The UUID of the Scene to be read.
  * @param ingestLocation Where the Scene was ingested to the first time (S3, etc.).
  * @param colorCorrections Settings for applying color correction to the Layer.
  */
@JsonCodec
final case class ExportLayerDefinition(
    layerId: UUID,
    sceneType: SceneType,
    ingestLocation: URI,
    colorCorrections: Option[ColorCorrect.Params]
)
