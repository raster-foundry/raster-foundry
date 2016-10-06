package com.azavea.rf.utils.queryparams

import java.util.UUID
import java.sql.Timestamp
import java.time.Instant

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters
import akka.http.scaladsl.unmarshalling._
import com.azavea.rf.database.query._


/** Unmarshalls query parameters to correct type */
trait QueryParameterDeserializers {

  implicit val deserializerUUID: Unmarshaller[String, UUID] = Unmarshaller.strict[String, UUID] {s =>
    UUID.fromString(s)
  }

  implicit val deserializerTimestamp: Unmarshaller[String, Timestamp] = Unmarshaller.strict[String, Timestamp] { s =>
    Timestamp.from(Instant.parse(s))
  }

}

trait QueryParametersCommon extends QueryParameterDeserializers {
  def bucketQueryParameters = (
    orgQueryParams & userQueryParameters & timestampQueryParameters
  ).as(BucketQueryParameters)

  def orgQueryParams = parameters(
    'organization.as(deserializerUUID).*
  ).as(OrgQueryParameters)

  def userQueryParameters = parameters(
    (
    'createdBy.as[String].?,
    'modifiedBy.as[String].?
    )
  ).as(UserQueryParameters)

  def timestampQueryParameters = parameters(
    (
      'minCreateDatetime.as(deserializerTimestamp).?,
      'maxCreateDatetime.as(deserializerTimestamp).?,
      'minModifiedDatetime.as(deserializerTimestamp).?,
      'maxModifiedDatetime.as(deserializerTimestamp).?
    )
  ).as(TimestampQueryParameters)
}
