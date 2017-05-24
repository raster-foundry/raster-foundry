package com.azavea.rf.api.grid

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import com.azavea.rf.database.query._
import com.azavea.rf.api.image.ImageQueryParametersDirective
import com.azavea.rf.api.utils.queryparams._

/** Trait to abstract out query parameters for scenes */
trait GridQueryParameterDirective extends QueryParametersCommon
    with ImageQueryParametersDirective {

  val gridSpecificQueryParams = parameters((
    'maxCloudCover.as[Float].?,
    'minCloudCover.as[Float].?,
    'minAcquisitionDatetime.as(deserializerTimestamp).?,
    'maxAcquisitionDatetime.as(deserializerTimestamp).?,
    'datasource.as[UUID].*,
    'month.as[Int].*,
    'minDayOfMonth.as[Int].?,
    'maxDayOfMonth.as[Int].?,
    'maxSunAzimuth.as[Float].?,
    'minSunAzimuth.as[Float].?,
    'maxSunElevation.as[Float].?,
    'minSunElevation.as[Float].?,
    'ingested.as[Boolean].?,
    'ingestStatus.as[String].*
  )).as(GridQueryParameters.apply _)

  val gridQueryParameters = (orgQueryParams &
    userQueryParameters &
    timestampQueryParameters &
    gridSpecificQueryParams &
    imageSpecificQueryParams
  ).as(CombinedGridQueryParams.apply _)
}
