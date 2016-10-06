package com.azavea.rf.image

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters
import com.azavea.rf.database.query.{CombinedImageQueryParams, ImageQueryParameters}

import com.azavea.rf.utils.queryparams._

/** Trait to mix in for image specific query parameters */
trait ImageQueryParametersDirective extends QueryParametersCommon {

  val imageSpecificQueryParams = parameters(
    'minRawDataBytes.as[Int].?,
    'maxRawDataBytes.as[Int].?,
    'scene.as[UUID].*
  ).as(ImageQueryParameters)

  val imageQueryParameters = (orgQueryParams &
    timestampQueryParameters &
    imageSpecificQueryParams
  ).as(CombinedImageQueryParams)

}
