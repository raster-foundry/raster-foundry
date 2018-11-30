package com.rasterfoundry.api.platform

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import com.rasterfoundry.datamodel._
import com.rasterfoundry.api.utils.queryparams._

trait PlatformQueryParameterDirective extends QueryParametersCommon {
  def platformQueryParameters =
    (
      timestampQueryParameters &
        userAuditQueryParameters &
        searchParams &
        activationParams
    ).as(PlatformQueryParameters.apply _)
}
