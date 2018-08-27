package com.azavea.rf.datamodel

import java.util.UUID

import io.circe.generic.JsonCodec

final case class SceneToProject(sceneId: UUID,
                                projectId: UUID,
                                /* Has a Scene been accepted by a user?
                                 * TRUE if a user added this Scene manually as usual from the UI, or if
                                 * they accepted this Scene from a list of "pending" Scenes which passed an AOI
                                 * check. Defaults to FALSE when added by an AOI check via a batch process.
                                 *
                                 * Scenes marked FALSE here should not appear during normal tiling
                                 * activities in the UI.
                                 */
                                accepted: Boolean,
                                sceneOrder: Option[Int] = None,
                                colorCorrectParams: ColorCorrect.Params)

final case class SceneToProjectwithSceneType(
    sceneId: UUID,
    projectId: UUID,
    accepted: Boolean,
    sceneOrder: Option[Int] = None,
    colorCorrectParams: ColorCorrect.Params,
    sceneType: Option[SceneType] = None,
    ingestLocation: Option[String]
)

@JsonCodec
final case class SceneCorrectionParams(sceneId: UUID,
                                       params: ColorCorrect.Params)
@JsonCodec
final case class BatchParams(items: List[SceneCorrectionParams])
@JsonCodec
final case class ProjectColorModeParams(redBand: Int,
                                        greenBand: Int,
                                        blueBand: Int)
