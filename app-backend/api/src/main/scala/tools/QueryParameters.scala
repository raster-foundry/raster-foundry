package com.rasterfoundry.api.tool

import com.rasterfoundry.api.utils.queryparams._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

trait ToolQueryParameterDirective extends QueryParametersCommon {

  val toolSpecificQueryParams = parameters(
    'singleSource.as[Boolean].?
  ).as(ToolQueryParameters.apply _)
  def combinedToolQueryParams =
    (
      toolSpecificQueryParams &
        orgQueryParams &
        userQueryParameters &
        timestampQueryParameters &
        searchParams &
        ownerQueryParameters &
        ownershipTypeQueryParameters &
        groupQueryParameters
    ).as(CombinedToolQueryParameters.apply _)
}
