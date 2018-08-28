package com.azavea.rf.api.platform

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import com.azavea.rf.datamodel._
import com.azavea.rf.api.utils.queryparams._

trait PlatformQueryParameterDirective extends QueryParametersCommon {
  def platformQueryParameters =
    (
      timestampQueryParameters &
        userAuditQueryParameters &
        searchParams &
        activationParams
    ).as(PlatformQueryParameters.apply _)
}
