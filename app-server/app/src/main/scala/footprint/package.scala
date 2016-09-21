package com.azavea.rf

import com.azavea.rf.utils.PaginatedResponse


/**
  * Json formats for footprint
  */
package object footprint extends RfJsonProtocols {
  implicit val footprintWithGeojsonFormat = jsonFormat5(FootprintWithGeojson.apply)
  implicit val footprintWithGeojsonCreateFormat = jsonFormat4(FootprintWithGeojsonCreate)

  implicit val paginatedFootprintWithGeojsonFormat = jsonFormat6(PaginatedResponse[FootprintWithGeojson])
}
