package com.azavea.rf.scene

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters
import com.azavea.rf.database.query._

import com.azavea.rf.utils.queryparams._

///** Trait to abstract out query parameters for scenes */
trait SceneQueryParameterDirective extends QueryParametersCommon {

  val sceneSpecificQueryParams = parameters((
    'maxCloudCover.as[Float].?,
    'minCloudCover.as[Float].?,
    'minAcquisitionDatetime.as(deserializerTimestamp).?,
    'maxAcquisitionDatetime.as(deserializerTimestamp).?,
    'datasource.as[String].*,
    'month.as[Int].*,
    'maxSunAzimuth.as[Float].?,
    'minSunAzimuth.as[Float].?,
    'maxSunElevation.as[Float].?,
    'minSunElevation.as[Float].?,
    'bbox.as[String].?,
    'point.as[String].?
  )).as(SceneQueryParameters)

  val sceneQueryParameters = (orgQueryParams &
    userQueryParameters &
    timestampQueryParameters &
    sceneSpecificQueryParams
  ).as(CombinedSceneQueryParams)
}
