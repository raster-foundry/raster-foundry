package com.azavea.rf

import com.azavea.rf.datamodel.latest.schema.tables.ScenesRow
import com.azavea.rf.utils.PaginatedResponse

import spray.json._

import geotrellis.vector.io._
import geotrellis.vector.Geometry
import geotrellis.slick.Projected

import com.azavea.rf.datamodel.enums._


/**
  * Json formats for scenes
  */
package object scene extends RfJsonProtocols {

  implicit object FootprintFormat extends RootJsonFormat[Projected[Geometry]] {
    def write(multipolygon: Projected[Geometry]) = {
      multipolygon.geom.toGeoJson.parseJson.asJsObject
    }

    def read(value: JsValue) =
      Projected(value.asJsObject.convertTo[Geometry], 3857)
  }

  // Formats for creating a scene
  implicit val sceneThumbnailFormat = jsonFormat5(SceneThumbnail)
  implicit val sceneImageFormat = jsonFormat7(SceneImage)
  implicit val createSceneFormat =  jsonFormat18(CreateScene)
  implicit val scenesRowWithOrgsFormat = jsonFormat21(ScenesRow)
  implicit val sceneWithRelatedFormat = jsonFormat22(SceneWithRelated.apply)
  implicit val paginatedScenesFormat = jsonFormat6(PaginatedResponse[SceneWithRelated])
}
