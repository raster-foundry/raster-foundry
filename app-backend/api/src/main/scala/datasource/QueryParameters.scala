package com.azavea.rf.api.datasource

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import com.azavea.rf.datamodel._
import com.azavea.rf.api.utils.queryparams._

trait DatasourceQueryParameterDirective extends QueryParametersCommon {
  def datasourceQueryParams =
    (
      userQueryParameters &
        searchParams &
        ownershipTypeQueryParameters &
        groupQueryParameters
    ).as(DatasourceQueryParameters.apply _)
}
