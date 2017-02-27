package com.azavea.rf.api

import com.azavea.rf.datamodel._


package object tool extends RfJsonProtocols {
  implicit val paginatedToolFormat = jsonFormat6(PaginatedResponse[Tool.WithRelated])
}
