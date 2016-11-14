package com.azavea.rf

import com.azavea.rf.datamodel._


package object modeltag extends RfJsonProtocols {
  implicit val paginatedModelTagsFormat = jsonFormat6(PaginatedResponse[ModelTag])
}