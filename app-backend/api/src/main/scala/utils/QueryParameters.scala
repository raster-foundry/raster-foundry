package com.azavea.rf.api.utils.queryparams

import java.util.UUID
import java.sql.Timestamp
import javax.xml.bind.DatatypeConverter

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
    Timestamp.from(DatatypeConverter.parseDateTime(s).getTime().toInstant())
  }

}

trait QueryParametersCommon extends QueryParameterDeserializers {
  def projectQueryParameters = (
    orgQueryParams & userQueryParameters & timestampQueryParameters
  ).as(ProjectQueryParameters.apply _)

  def aoiQueryParameters = (
    orgQueryParams & userQueryParameters & timestampQueryParameters
  ).as(AoiQueryParameters.apply _)

  def orgQueryParams = parameters(
    'organization.as(deserializerUUID).*
  ).as(OrgQueryParameters.apply _)

  def userQueryParameters = parameters(
    (
    'createdBy.as[String].?,
    'modifiedBy.as[String].?,
    'owner.as[String].?
    )
  ).as(UserQueryParameters.apply _)

  def timestampQueryParameters = parameters(
    (
      'minCreateDatetime.as(deserializerTimestamp).?,
      'maxCreateDatetime.as(deserializerTimestamp).?,
      'minModifiedDatetime.as(deserializerTimestamp).?,
      'maxModifiedDatetime.as(deserializerTimestamp).?
    )
  ).as(TimestampQueryParameters.apply _)
}
