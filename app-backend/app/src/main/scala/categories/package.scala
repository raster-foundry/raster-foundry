package com.azavea.rf

import com.azavea.rf.datamodel._


package object toolcategory extends RfJsonProtocols {
  implicit val paginatedToolCategoriesFormat = jsonFormat6(PaginatedResponse[ToolCategory])
}
