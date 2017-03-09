package com.azavea.rf.datamodel

import spray.json._
import spray.json.DefaultJsonProtocol._

import java.util.UUID

case class MosaicDefinition(sceneId: UUID, colorCorrections: Option[ColorCorrect.Params])

object MosaicDefinition {
  implicit val defaultMosaicDefinitionFormat = jsonFormat2(MosaicDefinition.apply)

  def fromScenesToProjects(scenesToProjects: Seq[SceneToProject]): Seq[MosaicDefinition] = {
    scenesToProjects.map { case SceneToProject(sceneId, projectId, sceneOrder, colorCorrection) =>
      MosaicDefinition(sceneId, colorCorrection)
    }
  }
}
