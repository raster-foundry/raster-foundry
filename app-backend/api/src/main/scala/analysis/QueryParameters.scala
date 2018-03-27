package com.azavea.rf.api.analysis

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters
import com.azavea.rf.datamodel._
import com.azavea.rf.api.utils.queryparams._

trait AnalysisQueryParametersDirective extends QueryParametersCommon {
  val analysisSpecificQueryParams = parameters((
    'createdBy.as[String].?
  )).as(AnalysisQueryParameters.apply _)

  val analysisQueryParameters = (
    analysisSpecificQueryParams &
    timestampQueryParameters
  ).as(CombinedAnalysisQueryParameters.apply _)
}
