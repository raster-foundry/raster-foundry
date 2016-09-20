package com.azavea.rf.scene

import java.util.UUID
import java.sql.Timestamp
import java.time.Instant

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, Rejection}
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import com.azavea.rf.utils.queryparams._


/** Case class representing all possible query parameters */
case class SceneQueryParameters(
  maxCloudCover: Option[Float],
  minCloudCover: Option[Float],
  minAcquisitionDatetime: Option[Timestamp],
  maxAcquisitionDatetime: Option[Timestamp],
  datasource: Iterable[String],
  month: Iterable[Int],
  maxSunAzimuth: Option[Float],
  minSunAzimuth: Option[Float],
  maxSunElevation: Option[Float],
  minSunElevation: Option[Float]
)

/** Combined all query parameters */
case class CombinedSceneQueryParams(
  orgParams: OrgQueryParameters,
  userParams: UserQueryParameters,
  timestampParams: TimestampQueryParameters,
  sceneParams: SceneQueryParameters
)

/** Trait to abstract out query parameters for scenes */
trait SceneQueryParameterDirective extends QueryParametersCommon {

  val sceneSpecificQueryParams = parameters(
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
  ).as(SceneQueryParameters)

  val sceneQueryParameters = (orgQueryParams &
    userQueryParameters &
    timestampQueryParameters &
    sceneSpecificQueryParams
  ).as(CombinedSceneQueryParams)

}
