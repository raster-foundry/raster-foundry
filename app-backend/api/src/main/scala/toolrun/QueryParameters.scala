package com.rasterfoundry.api.toolrun

import com.rasterfoundry.api.utils.queryparams._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import java.util.UUID

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
      ownerQueryParameters &
      ownershipTypeQueryParameters &
      groupQueryParameters &
      userQueryParameters &
      searchParams
  ).as(CombinedToolRunQueryParameters.apply _)
}
