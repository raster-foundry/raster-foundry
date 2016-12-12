package com.azavea.rf.grid

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import com.azavea.rf.database.query._
import com.azavea.rf.image.ImageQueryParametersDirective
import com.azavea.rf.utils.queryparams._

/** Trait to abstract out query parameters for scenes */
trait GridQueryParameterDirective extends QueryParametersCommon
    with ImageQueryParametersDirective {

  val gridSpecificQueryParams = parameters((
    'maxCloudCover.as[Float].?,
    'minCloudCover.as[Float].?,
    'minAcquisitionDatetime.as(deserializerTimestamp).?,
    'maxAcquisitionDatetime.as(deserializerTimestamp).?,
    'datasource.as[String].*,
    'month.as[Int].*,
    'maxSunAzimuth.as[Float].?,
    'minSunAzimuth.as[Float].?,
    'maxSunElevation.as[Float].?,
    'minSunElevation.as[Float].?
  )).as(GridQueryParameters)

  val gridQueryParameters = (orgQueryParams &
    userQueryParameters &
    timestampQueryParameters &
    gridSpecificQueryParams &
    imageSpecificQueryParams
  ).as(CombinedGridQueryParams)
}
