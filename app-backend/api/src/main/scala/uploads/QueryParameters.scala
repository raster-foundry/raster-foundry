package com.rasterfoundry.api.uploads

import com.rasterfoundry.api.utils.queryparams._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import java.util.UUID

trait UploadQueryParameterDirective extends QueryParametersCommon {
  val uploadQueryParams = parameters(
    (
      'datasource.as[UUID].?,
      'uploadStatus.as[String].?,
      'projectId.as[UUID].?,
      'layerId.as[UUID].?
    )).as(UploadQueryParameters.apply _)
}
