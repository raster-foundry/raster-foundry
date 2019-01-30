package com.rasterfoundry.api.toolrun

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters
import com.rasterfoundry.datamodel._
import com.rasterfoundry.api.utils.queryparams._

trait ToolRunQueryParametersDirective extends QueryParametersCommon {
  val toolRunSpecificQueryParams = parameters(
    (
      'createdBy.as[String].?,
      'projectId.as[UUID].?,
      'templateId.as[UUID].?,
      'projectLayerId.as[UUID].?
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
