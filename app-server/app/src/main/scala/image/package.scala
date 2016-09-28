package com.azavea.rf

import com.azavea.rf.utils.PaginatedResponse
import com.azavea.rf.datamodel.latest.schema.tables.{ImagesRow}

package object image extends RfJsonProtocols {

  implicit val paginatedImagesFormat = jsonFormat6(PaginatedResponse[ImagesRow])
  implicit val createImageFormat = jsonFormat8(CreateImage)

}
