package com.azavea.rf.api

import com.azavea.rf.datamodel._

package object project extends RfJsonProtocols {
  implicit val paginatedProjectFormat = jsonFormat6(PaginatedResponse[Project])
}
