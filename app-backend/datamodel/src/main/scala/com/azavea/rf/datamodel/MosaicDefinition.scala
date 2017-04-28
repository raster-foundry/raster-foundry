package com.azavea.rf.datamodel

import java.util.UUID

import io.circe._
import io.circe.generic.JsonCodec

@JsonCodec
case class MosaicDefinition(sceneId: UUID, colorCorrections: Option[ColorCorrect.Params])

object MosaicDefinition {
  def fromScenesToProjects(scenesToProjects: Seq[SceneToProject]): Seq[MosaicDefinition] = {
    scenesToProjects.map { case SceneToProject(sceneId, _, _, _, colorCorrection) =>
      MosaicDefinition(sceneId, colorCorrection)
    }
  }
}
