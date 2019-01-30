package com.rasterfoundry.api.maptoken

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters
import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.api.utils.queryparams._

/** Trait to mix in for image specific query parameters */
trait MapTokensQueryParameterDirective extends QueryParametersCommon {

  val mapTokenSpecificQueryParams = parameters(
    'name.as[String].?,
    'project.as[UUID].?
  ).as(MapTokenQueryParameters.apply _)

  val mapTokenQueryParams = (
    orgQueryParams &
      userQueryParameters &
      mapTokenSpecificQueryParams
  ).as(CombinedMapTokenQueryParameters.apply _)
}
