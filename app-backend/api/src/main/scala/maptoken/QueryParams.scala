package com.azavea.rf.api.maptoken

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters
import com.azavea.rf.database.query.{CombinedMapTokenQueryParameters, MapTokenQueryParameters}
import com.azavea.rf.api.utils.queryparams._

/** Trait to mix in for image specific query parameters */
trait MapTokensQueryParameterDirective extends QueryParametersCommon {

  val mapTokenSpecificQueryParams = parameters(
    'name.as[String].?,
    'project.as[UUID].?
  ).as(MapTokenQueryParameters)

  val mapTokenQueryParams = (
    orgQueryParams &
    userQueryParameters &
    mapTokenSpecificQueryParams
    ).as(CombinedMapTokenQueryParameters)
}
