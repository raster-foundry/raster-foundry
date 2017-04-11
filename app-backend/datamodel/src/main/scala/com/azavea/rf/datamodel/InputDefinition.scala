package com.azavea.rf.datamodel

import com.azavea.rf.datamodel._
import io.circe.generic.JsonCodec

import geotrellis.vector._
import geotrellis.vector.io._

import java.util.UUID

@JsonCodec
case class InputDefinition(
  projectId: UUID,
  resolution: Int,
  layers: Array[ExportLayerDefinition],
  mask: Option[MultiPolygon]
)
