package com.rasterfoundry.api.toolcategory

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import com.rasterfoundry.datamodel._
import com.rasterfoundry.api.utils.queryparams._

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
