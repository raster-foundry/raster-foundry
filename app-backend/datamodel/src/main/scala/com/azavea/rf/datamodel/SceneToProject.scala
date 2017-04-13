package com.azavea.rf.datamodel

import java.util.UUID

import io.circe.generic.JsonCodec

case class SceneToProject(
  sceneId: UUID,
  projectId: UUID,
  accepted: Boolean, /* Has a Scene been accepted from an AOI check? */
  sceneOrder: Option[Int] = None,
  colorCorrectParams: Option[ColorCorrect.Params] = None
)

@JsonCodec
case class SceneCorrectionParams(sceneId: UUID, params: ColorCorrect.Params)
@JsonCodec
case class BatchParams(items: List[SceneCorrectionParams])
