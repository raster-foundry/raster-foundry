package com.azavea.rf.tool.params

import com.azavea.rf.tool.ast._

import io.circe.generic.JsonCodec

import java.util.UUID


@JsonCodec
case class EvalParams(sources: Map[UUID, RFMLRaster], metadataOverrides: Map[UUID, NodeMetadata])

