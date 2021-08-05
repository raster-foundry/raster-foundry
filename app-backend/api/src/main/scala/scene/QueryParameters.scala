package com.rasterfoundry.api.scene

import com.rasterfoundry.api.utils.queryparams._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import java.util.UUID

///** Trait to abstract out query parameters for scenes */
trait SceneQueryParameterDirective extends QueryParametersCommon {

  val sceneSpecificQueryParams = parameters(
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
    'bbox.as[String].*,
    'point.as[String].?,
    'project.as[UUID].?,
    'layer.as[UUID].?,
    'ingested.as[Boolean].?,
    'ingestStatus.as[String].*,
    'pending.as[Boolean].?,
    'shape.as[UUID].?,
    'projectLayerShape.as[UUID].?
  ).as(SceneQueryParameters.apply _)

  val sceneSearchModeQueryParams = parameters(
    ('exactCount.as[Boolean].?)
  ).as(SceneSearchModeQueryParams.apply _)

  val sceneQueryParameters = (orgQueryParams &
    userQueryParameters &
    timestampQueryParameters &
    sceneSpecificQueryParams &
    ownershipTypeQueryParameters &
    groupQueryParameters &
    sceneSearchModeQueryParams).as(CombinedSceneQueryParams.apply _)
}
