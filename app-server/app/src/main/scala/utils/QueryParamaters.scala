package com.azavea.rf.utils.queryparams

import java.util.UUID
import java.sql.Timestamp
import java.time.Instant

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters
import akka.http.scaladsl.unmarshalling._


/** Unmarshalls query parameters to correct type */
trait QueryParameterDeserializers {

  implicit val deserializerUUID: Unmarshaller[String, UUID] = Unmarshaller.strict[String, UUID] {s =>
    UUID.fromString(s)
  }

  implicit val deserializerTimestamp: Unmarshaller[String, Timestamp] = Unmarshaller.strict[String, Timestamp] { s =>
    Timestamp.from(Instant.parse(s))
  }

}

/** Common query parameters for models that hvae organization attributes */
case class OrgQueryParameters(
  organization: Iterable[UUID]
)


/** Query parameters to filter by users */
case class UserQueryParameters(
  createdBy: Option[String],
  modifiedBy: Option[String]
)


/** Query parameters to filter by modified/created times */
case class TimestampQueryParameters(
  minCreateDatetime: Option[Timestamp],
  maxCreateDatetime: Option[Timestamp],
  minModifiedDatetime: Option[Timestamp],
  maxModifiedDatetime: Option[Timestamp]
)


trait QueryParametersCommon extends QueryParameterDeserializers {

  val orgQueryParams = parameters(
    'organization.as(deserializerUUID).*
  ).as(OrgQueryParameters)

  val userQueryParameters = parameters(
    'createdBy.as[String].?,
    'modifiedBy.as[String].?
  ).as(UserQueryParameters)

  val timestampQueryParameters = parameters(
    'minCreateDatetime.as(deserializerTimestamp).?,
    'maxCreateDatetime.as(deserializerTimestamp).?,
    'minModifiedDatetime.as(deserializerTimestamp).?,
    'maxModifiedDatetime.as(deserializerTimestamp).?
  ).as(TimestampQueryParameters)


}
