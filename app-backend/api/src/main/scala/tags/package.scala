package com.azavea.rf.api

import com.azavea.rf.datamodel._


package object tooltag extends RfJsonProtocols {
  implicit val paginatedToolTagsFormat = jsonFormat6(PaginatedResponse[ToolTag])
}
