package com.rasterfoundry.api.thumbnail

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters
import com.rasterfoundry.datamodel._

import com.rasterfoundry.api.utils.queryparams._

trait ThumbnailQueryParameterDirective extends QueryParametersCommon {

  val thumbnailSpecificQueryParameters = parameters(
    'sceneId.as[UUID].?
  ).as(ThumbnailQueryParameters.apply _)
}
