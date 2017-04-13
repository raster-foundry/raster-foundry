package com.azavea.rf.datamodel

import java.util.UUID

import io.circe.generic.JsonCodec

case class SceneToProject(
  sceneId: UUID,
  projectId: UUID,

  /* Has a Scene been accepted by a user?
   * TRUE if a user added this Scene manually as usual from the UI, or if
   * they accepted this Scene from a list of "pending" Scenes which passed an AOI
   * check. Defaults to FALSE when added by an AOI check via an Airflow process.
   *
   * Scenes marked FALSE here should not appear during normal tiling
   * activities in the UI.
   */
  accepted: Boolean,

  sceneOrder: Option[Int] = None,
  colorCorrectParams: Option[ColorCorrect.Params] = None
)

@JsonCodec
case class SceneCorrectionParams(sceneId: UUID, params: ColorCorrect.Params)
@JsonCodec
case class BatchParams(items: List[SceneCorrectionParams])
