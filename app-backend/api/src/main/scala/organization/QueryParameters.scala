package com.rasterfoundry.api.organization

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import com.rasterfoundry.datamodel._
import com.rasterfoundry.api.utils.queryparams._

trait OrganizationQueryParameterDirective extends QueryParametersCommon {

  def organizationQueryParameters =
    (
      timestampQueryParameters &
        searchParams &
        activationParams &
        platformIdParams
    ).as(OrganizationQueryParameters.apply _)

}
