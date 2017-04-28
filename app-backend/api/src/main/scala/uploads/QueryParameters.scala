package com.azavea.rf.api.uploads

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters
import com.azavea.rf.database.query._
import com.azavea.rf.api.utils.queryparams._

trait UploadQueryParameterDirective extends QueryParametersCommon {
  val uploadQueryParams = parameters((
    'datasource.as[UUID].?,
    'organization.as[UUID].?,
    'uploadStatus.as[String].?
  )).as(UploadQueryParameters.apply _)
}
