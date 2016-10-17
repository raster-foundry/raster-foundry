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
  implicit val paginatedScenesFormat = jsonFormat6(PaginatedResponse[Scene.WithRelated])
}
