package com.rasterfoundry.common.datamodel

import com.rasterfoundry.common._
import geotrellis.vector.MultiPolygon
import io.circe.Json
import io.circe.generic.JsonCodec

import java.util.UUID

@JsonCodec
final case class MosaicDefinition(sceneId: UUID,
                                  colorCorrections: ColorCorrect.Params,
                                  sceneType: Option[SceneType] = None,
                                  ingestLocation: Option[String],
                                  footprint: Option[MultiPolygon],
                                  isSingleBand: Boolean,
                                  singleBandOptions: Option[Json],
                                  mask: Option[MultiPolygon])
