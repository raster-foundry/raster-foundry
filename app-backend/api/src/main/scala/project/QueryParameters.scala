package com.rasterfoundry.api.project

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.api.utils.queryparams._

///** Trait to abstract out query parameters for scenes */
trait ProjectSceneQueryParameterDirective extends QueryParametersCommon {

  val projectSceneQueryParameters = parameters(
    (
      'ingested.as[Boolean].?,
      'ingestStatus.as[String].*,
      'accepted.as[Boolean].?
    )
  ).as(ProjectSceneQueryParameters.apply _)
}
