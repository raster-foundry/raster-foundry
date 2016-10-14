package com.azavea.rf

import com.azavea.rf.datamodel._
import geotrellis.vector.io._
import geotrellis.vector.Geometry
import geotrellis.slick.Projected
import geotrellis.proj4._

import spray.json._

/**
  * Json formats for scenes
  */
package object scene extends RfJsonProtocols {

  implicit object FootprintFormat extends RootJsonFormat[Projected[Geometry]] {
    def write(multipolygon: Projected[Geometry]) = {
      val latlngProjected = multipolygon.reproject(WebMercator, LatLng)(4326)
      latlngProjected.geom.toGeoJson.parseJson.asJsObject
    }

    def read(value: JsValue) =
      Projected(value.asJsObject.convertTo[Geometry], 3857)
  }

  implicit val paginatedScenesFormat = jsonFormat6(PaginatedResponse[Scene.WithRelated])
}
