package com.azavea.rf.api.image

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters
import com.azavea.rf.database.query.{CombinedImageQueryParams, ImageQueryParameters}

import com.azavea.rf.api.utils.queryparams._

/** Trait to mix in for image specific query parameters */
trait ImageQueryParametersDirective extends QueryParametersCommon {

  val imageSpecificQueryParams = parameters(
    'minRawDataBytes.as[Int].?,
    'maxRawDataBytes.as[Int].?,
    'minResolution.as[Float].?,
    'maxResolution.as[Float].?,
    'scene.as[UUID].*
  ).as(ImageQueryParameters)

  val imageQueryParameters = (orgQueryParams &
    timestampQueryParameters &
    imageSpecificQueryParams
  ).as(CombinedImageQueryParams)

}
