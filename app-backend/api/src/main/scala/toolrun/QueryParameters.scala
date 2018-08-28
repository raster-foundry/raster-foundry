package com.azavea.rf.api.toolrun

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters
import com.azavea.rf.datamodel._
import com.azavea.rf.api.utils.queryparams._

trait ToolRunQueryParametersDirective extends QueryParametersCommon {
  val toolRunSpecificQueryParams = parameters(
    (
      'createdBy.as[String].?,
      'projectId.as[UUID].?,
      'toolId.as[UUID].?
    )).as(ToolRunQueryParameters.apply _)

  val toolRunQueryParameters = (
    toolRunSpecificQueryParams &
      timestampQueryParameters &
      ownershipTypeQueryParameters &
      groupQueryParameters &
      userQueryParameters &
      searchParams
  ).as(CombinedToolRunQueryParameters.apply _)
}
