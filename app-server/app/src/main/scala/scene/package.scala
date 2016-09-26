package com.azavea.rf

import com.azavea.rf.utils.PaginatedResponse
import com.azavea.rf.datamodel.latest.schema.tables.{ScenesRow}


/**
  * Json formats for scenes
  */
package object scene extends RfJsonProtocols {

  implicit val scenesRowWithOrgsFormat = jsonFormat20(ScenesRow)
  implicit val createSceneFormat =  jsonFormat15(CreateScene)
  implicit val paginatedScenesFormat = jsonFormat6(PaginatedResponse[ScenesRow])
}
