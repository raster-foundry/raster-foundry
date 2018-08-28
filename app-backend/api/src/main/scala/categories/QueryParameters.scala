package com.azavea.rf.api.toolcategory

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import com.azavea.rf.datamodel._
import com.azavea.rf.api.utils.queryparams._

/* Trait to abstract out query parameters for tool categories */
trait ToolCategoryQueryParametersDirective extends QueryParametersCommon {
  val toolCategorySpecificQueryParams = parameters(
    (
      'search.as[String].?
    )).as(ToolCategoryQueryParameters.apply _)

  val toolCategoryQueryParameters = (
    timestampQueryParameters &
      toolCategorySpecificQueryParams
  ).as(CombinedToolCategoryQueryParams.apply _)
}
