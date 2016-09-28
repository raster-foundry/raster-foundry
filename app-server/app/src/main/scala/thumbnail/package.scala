package com.azavea.rf

import com.azavea.rf.utils.PaginatedResponse
import com.azavea.rf.datamodel.latest.schema.tables.{ThumbnailsRow}

package object thumbnail extends RfJsonProtocols {

  implicit val thumbnailsRowFormat = jsonFormat9(ThumbnailsRow)
  implicit val createThumbnailFormat = jsonFormat6(CreateThumbnail)
  implicit val paginatedThumbnailFormat = jsonFormat6(PaginatedResponse[ThumbnailsRow])

}
