package com.azavea.rf.datamodel

import java.util.UUID
import java.sql.Timestamp

case class SceneToProject(
  sceneId: UUID,
  projectId: UUID,
  sceneOrder: Option[Int] = None,
  colorCorrectParams: Option[ColorCorrect.Params] = None
)

case class SceneCorrectionParams(sceneId: UUID, params: ColorCorrect.Params)
case class BatchParams(items: List[SceneCorrectionParams])
