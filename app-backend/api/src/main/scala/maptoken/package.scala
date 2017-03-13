package com.azavea.rf.api

import com.azavea.rf.datamodel._

package object maptoken extends RfJsonProtocols {

  implicit val paginatedMapToken = jsonFormat6(PaginatedResponse[MapToken])

}
