 package com.azavea.rf.datamodel

import java.util.UUID

import io.circe.generic.JsonCodec
import com.azavea.rf.tool.ast.MapAlgebraAST
import com.azavea.rf.tool.params.EvalParams

@JsonCodec
case class InputDefinition(
  projectId: UUID,  // TODO: Might not be necessary.
  resolution: Int,
  ast: MapAlgebraAST,
  params: EvalParams,
  colorCorrections: Option[ColorCorrect.Params]
)
