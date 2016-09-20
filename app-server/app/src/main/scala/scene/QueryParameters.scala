package com.azavea.rf.scene

import java.util.UUID
import java.sql.Timestamp
import java.time.Instant

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, Rejection}
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters
import akka.http.scaladsl.unmarshalling._


/** Case class representing all possible query parameters */
case class SceneQueryParameters(
  organization: Iterable[UUID],
  createdBy: Option[String],
  modifiedBy: Option[String],
  maxCloudCover: Option[Float],
  minCloudCover: Option[Float],
  minAcquisitionDatetime: Option[Timestamp],
  maxAcquisitionDatetime: Option[Timestamp],
  minCreateDatetime: Option[Timestamp],
  maxCreateDatetime: Option[Timestamp],
  datasource: Iterable[String],
  month: Iterable[Int],
  maxSunAzimuth: Option[Float],
  minSunAzimuth: Option[Float],
  maxSunElevation: Option[Float],
  minSunElevation: Option[Float],
  tags: Iterable[String]
)


/** Trait to abstract out query parameters for scenes */
trait SceneQueryParameterDirective {

  implicit val deserializerUUID: Unmarshaller[String, UUID] = Unmarshaller.strict[String, UUID] {s =>
    UUID.fromString(s)
  }

  implicit val deserializerTimestamp: Unmarshaller[String, Timestamp] = Unmarshaller.strict[String, Timestamp] { s =>
    Timestamp.from(Instant.parse(s))
  }

  val queryParams = parameters(
    'organization.as(deserializerUUID).*,
    'createdBy.as[String].?,
    'modifiedBy.as[String].?,
    'maxCloudCover.as[Float].?,
    'minCloudCover.as[Float].?,
    'minAcquisitionDatetime.as(deserializerTimestamp).?,
    'maxAcquisitionDatetime.as(deserializerTimestamp).?,
    'minCreateDatetime.as(deserializerTimestamp).?,
    'maxCreateDatetime.as(deserializerTimestamp).?,
    'datasource.as[String].*,
    'month.as[Int].*,
    'maxSunAzimuth.as[Float].?,
    'minSunAzimuth.as[Float].?,
    'maxSunElevation.as[Float].?,
    'minSunElevation.as[Float].?,
    'tags.as[String].*
  ).as(SceneQueryParameters)

}
