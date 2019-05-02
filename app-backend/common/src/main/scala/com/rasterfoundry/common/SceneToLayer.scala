package com.rasterfoundry.common

import geotrellis.vector.{Geometry, Projected}
import io.circe.generic.JsonCodec
import io.circe.Json
import java.util.UUID

import com.rasterfoundry.common.color._
import com.rasterfoundry.datamodel.SceneType

final case class SceneToLayer(sceneId: UUID,
                              projectLayerId: UUID,
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

final case class SceneToLayerWithSceneType(
    sceneId: UUID,
    projectId: UUID,
    projectLayerId: UUID,
    accepted: Boolean,
    sceneOrder: Option[Int] = None,
    colorCorrectParams: ColorCorrect.Params,
    sceneType: Option[SceneType] = None,
    ingestLocation: Option[String],
    dataFootprint: Option[Projected[Geometry]],
    isSingleBand: Boolean,
    singleBandOptions: Option[Json],
    mask: Option[Projected[Geometry]]
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
