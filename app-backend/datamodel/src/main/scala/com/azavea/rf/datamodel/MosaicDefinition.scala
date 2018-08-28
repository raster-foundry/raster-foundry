package com.azavea.rf.datamodel

import java.util.UUID

import io.circe.generic.JsonCodec

@JsonCodec
final case class MosaicDefinition(sceneId: UUID,
                                  colorCorrections: ColorCorrect.Params,
                                  sceneType: Option[SceneType] = None,
                                  ingestLocation: Option[String])

object MosaicDefinition {
  def fromScenesToProjects(
      scenesToProjects: Seq[SceneToProjectwithSceneType]
  ): Seq[MosaicDefinition] = {
    scenesToProjects.map {
      case SceneToProjectwithSceneType(
          sceneId,
          _,
          _,
          _,
          colorCorrection,
          sceneType,
          ingestLocation
          ) =>
        MosaicDefinition(sceneId, colorCorrection, sceneType, ingestLocation)
    }
  }
  def fromScenesToProjects(scenesToProjects: Seq[SceneToProjectwithSceneType],
                           redBand: Int,
                           greenBand: Int,
                           blueBand: Int): Seq[MosaicDefinition] = {
    scenesToProjects.map {
      case SceneToProjectwithSceneType(
          sceneId,
          _,
          _,
          _,
          colorCorrection,
          sceneType,
          ingestLocation
          ) => {
        val ccp = colorCorrection.copy(
          redBand = redBand,
          greenBand = greenBand,
          blueBand = blueBand
        )
        MosaicDefinition(sceneId, ccp, sceneType, ingestLocation)
      }
    }
  }
}
