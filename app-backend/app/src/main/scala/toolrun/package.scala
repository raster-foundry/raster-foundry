package com.azavea.rf

import com.azavea.rf.datamodel.{PaginatedResponse, ToolRun}

package object toolrun extends RfJsonProtocols {
    implicit val paginatedToolRunFormat = jsonFormat6(PaginatedResponse[ToolRun])
}