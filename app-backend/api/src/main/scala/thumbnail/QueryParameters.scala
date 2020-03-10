package com.rasterfoundry.api.thumbnail

import com.rasterfoundry.api.utils.queryparams._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import java.util.UUID

trait ThumbnailQueryParameterDirective extends QueryParametersCommon {

  val thumbnailSpecificQueryParameters = parameters(
    'sceneId.as[UUID].?
  ).as(ThumbnailQueryParameters.apply _)
}
