package com.azavea.rf.api

import com.azavea.rf.datamodel._

package object thumbnail extends RfJsonProtocols {

  implicit val paginatedThumbnailFormat = jsonFormat6(PaginatedResponse[Thumbnail])

}
