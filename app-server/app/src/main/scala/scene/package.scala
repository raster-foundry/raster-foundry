package com.azavea.rf

import com.azavea.rf.datamodel.latest.schema.tables.ScenesRow
import com.azavea.rf.utils.PaginatedResponse


/**
  * Json formats for scenes
  */
package object scene extends RfJsonProtocols {

  // Formats for creating a scene
  implicit val sceneThumbnailFormat = jsonFormat5(SceneThumbnail)
  implicit val sceneFootprint = jsonFormat1(SceneFootprint)
  implicit val sceneImageFormat = jsonFormat7(SceneImage)
  implicit val createSceneFormat =  jsonFormat18(CreateScene)

  implicit val scenesRowWithOrgsFormat = jsonFormat20(ScenesRow)
  implicit val sceneWithRelatedFormat = jsonFormat22(SceneWithRelated.apply)
  implicit val paginatedScenesFormat = jsonFormat6(PaginatedResponse[SceneWithRelated])
}
