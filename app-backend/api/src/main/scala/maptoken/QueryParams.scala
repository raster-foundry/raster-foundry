package com.rasterfoundry.api.maptoken

import com.rasterfoundry.api.utils.queryparams._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import java.util.UUID

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
