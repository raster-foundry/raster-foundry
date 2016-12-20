package com.azavea.rf.datamodel

import spray.json._
import spray.json.DefaultJsonProtocol._

import java.util.UUID

case class MosaicDefinition(definition: Seq[(UUID, Option[ColorCorrect.Params])])

object MosaicDefinition {
  implicit val defaultMosaicDefinitionFormat = jsonFormat1(MosaicDefinition.apply _)

  def fromScenesToProjects(scenesToProjects: Seq[SceneToProject]): MosaicDefinition =
    MosaicDefinition(
      scenesToProjects.map { case SceneToProject(sceneId, projectId, sceneOrder, colorCorrection) =>
        sceneId -> colorCorrection
      }
    )
}

