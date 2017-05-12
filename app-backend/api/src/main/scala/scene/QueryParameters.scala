package com.azavea.rf.api.scene

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import com.azavea.rf.database.query._
import com.azavea.rf.api.image.ImageQueryParametersDirective
import com.azavea.rf.api.utils.queryparams._

///** Trait to abstract out query parameters for scenes */
trait SceneQueryParameterDirective extends QueryParametersCommon
    with ImageQueryParametersDirective {

  val sceneSpecificQueryParams = parameters((
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
    'bbox.as[String].?,
    'point.as[String].?,
    'project.as[UUID].?,
    'ingested.as[Boolean].?,
    'ingestStatus.as[String].*,
    'pending.as[Boolean].?
  )).as(SceneQueryParameters.apply _)

  val sceneQueryParameters = (orgQueryParams &
    userQueryParameters &
    timestampQueryParameters &
    sceneSpecificQueryParams &
    imageSpecificQueryParams
  ).as(CombinedSceneQueryParams.apply _)
}
