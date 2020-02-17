package com.rasterfoundry.api.exports

import com.rasterfoundry.api.utils.queryparams._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import java.util.UUID

trait ExportQueryParameterDirective extends QueryParametersCommon {
  val exportQueryParams = parameters(
    (
      'organization.as[UUID].?,
      'project.as[UUID].?,
      'analysis.as[UUID].?,
      'exportStatus.as[String].*,
      'layer.as[UUID].?
    )).as(ExportQueryParameters.apply _)
}
