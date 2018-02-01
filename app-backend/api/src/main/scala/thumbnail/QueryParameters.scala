package com.azavea.rf.api.thumbnail

import java.util.UUID
import java.sql.Timestamp
import java.time.Instant

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, Rejection}
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters
import com.azavea.rf.datamodel._

import com.azavea.rf.api.utils.queryparams._

trait ThumbnailQueryParameterDirective extends QueryParametersCommon {

  val thumbnailSpecificQueryParameters = parameters(
    'sceneId.as[UUID].?
  ).as(ThumbnailQueryParameters.apply _)
}
