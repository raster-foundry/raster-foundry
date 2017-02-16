package com.azavea.rf.toolcategory

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import com.azavea.rf.database.query._
import com.azavea.rf.utils.queryparams._

/* Trait to abstract out query parameters for tool categories */
trait ToolCategoryQueryParametersDirective extends QueryParametersCommon {
  val toolCategorySpecificQueryParams = parameters((
    'search.as[String].?
  )).as(ToolCategoryQueryParameters)

  val toolCategoryQueryParameters = (
    timestampQueryParameters &
    toolCategorySpecificQueryParams
  ).as(CombinedToolCategoryQueryParams)
}
