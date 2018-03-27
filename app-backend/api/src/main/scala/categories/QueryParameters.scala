package com.azavea.rf.api.category

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import com.azavea.rf.datamodel._
import com.azavea.rf.api.utils.queryparams._

/* Trait to abstract out query parameters for categories */
trait CategoryQueryParametersDirective extends QueryParametersCommon {
  val categorySpecificQueryParams = parameters((
    'search.as[String].?
  )).as(CategoryQueryParameters.apply _)

  val categoryQueryParameters = (
    timestampQueryParameters &
    categorySpecificQueryParams
  ).as(CombinedCategoryQueryParams.apply _)
}
