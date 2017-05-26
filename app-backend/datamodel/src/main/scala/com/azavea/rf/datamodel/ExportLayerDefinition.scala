package com.azavea.rf.datamodel

import io.circe.generic.JsonCodec

import java.net.URI
import java.util.UUID

/**
  * @param layerId The UUID of the Scene to be read.
  * @param ingestLocation Where the Scene was ingested to the first time (S3, etc.).
  * @param colorCorrections Settings for applying color correction to the Layer.
  */
@JsonCodec
case class ExportLayerDefinition(
  layerId: UUID,
  ingestLocation: URI,
  colorCorrections: Option[ColorCorrect.Params]
)
