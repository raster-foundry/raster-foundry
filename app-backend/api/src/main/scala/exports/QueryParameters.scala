package com.azavea.rf.api.exports

import com.azavea.rf.datamodel._
import com.azavea.rf.api.utils.queryparams._

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import java.util.UUID

trait ExportQueryParameterDirective extends QueryParametersCommon {
  val exportQueryParams = parameters(
    (
      'organization.as[UUID].?,
      'project.as[UUID].?,
      'analysis.as[UUID].?,
      'exportStatus.as[String].*
    )).as(ExportQueryParameters.apply _)
}
