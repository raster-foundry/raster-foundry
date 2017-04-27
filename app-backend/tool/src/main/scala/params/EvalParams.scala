package com.azavea.rf.tool.params

import java.util.UUID

import com.azavea.rf.tool.ast._
import io.circe._
import io.circe.generic.JsonCodec

// --- //

// TODO: This is temporary! Use Nathan's real type.
@JsonCodec
case class NodeMetadata(id: UUID)

@JsonCodec
case class EvalParams(sources: Map[UUID, RFMLRaster], metas: Map[UUID, NodeMetadata])
