package com.rasterfoundry.api.tool

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.api.utils.queryparams._

trait ToolQueryParameterDirective extends QueryParametersCommon {
  def combinedToolQueryParams =
    (
      orgQueryParams &
        userQueryParameters &
        timestampQueryParameters &
        searchParams &
        ownershipTypeQueryParameters &
        groupQueryParameters
    ).as(CombinedToolQueryParameters.apply _)
}
