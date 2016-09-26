package com.azavea.rf.image

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import com.azavea.rf.utils.queryparams._


/** Case class for combined params for images */
case class CombinedImageQueryParams(
  orgParams: OrgQueryParameters,
  timestampParams: TimestampQueryParameters,
  imageParams: ImageQueryParameters
)


/** Query parameters specific to image files */
case class ImageQueryParameters(
  minRawDataBytes: Option[Int],
  maxRawDataBytes: Option[Int],
  scene: Iterable[UUID]
)


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
