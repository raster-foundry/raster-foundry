package com.azavea.rf.api.team

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import com.azavea.rf.datamodel._
import com.azavea.rf.api.utils.queryparams._

trait TeamQueryParameterDirective extends QueryParametersCommon {
  def teamQueryParameters = (
    timestampQueryParameters &
    orgQueryParams &
    userAuditQueryParameters &
    searchParams &
    activationParams
  ).as(TeamQueryParameters.apply _)
}
