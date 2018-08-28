package com.azavea.rf.api.tool

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import com.azavea.rf.datamodel._
import com.azavea.rf.api.utils.queryparams._

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
