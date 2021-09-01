package com.rasterfoundry.api.project

import com.rasterfoundry.api.utils.queryparams._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

///** Trait to abstract out query parameters for scenes */
trait ProjectSceneQueryParameterDirective extends QueryParametersCommon {

  val projectSceneQueryParameters = parameters(
    'ingested.as[Boolean].?,
    'ingestStatus.as[String].*,
    'accepted.as[Boolean].?
  ).as(ProjectSceneQueryParameters.apply _)

}
