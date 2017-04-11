package com.azavea.rf.datamodel

import io.circe.generic.JsonCodec

import java.net.URI
import java.util.UUID

@JsonCodec
case class ExportLayerDefinition(
  layerId: UUID,
  ingestLocation: URI,
  colorCorrections: Option[ColorCorrect.Params]
)
