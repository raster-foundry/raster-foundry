package com.azavea.rf.api.utils.queryparams

import com.azavea.rf.api._
import com.azavea.rf.datamodel._

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters
import akka.http.scaladsl.unmarshalling._

import java.util.UUID
import java.sql.Timestamp
import javax.xml.bind.DatatypeConverter


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
    orgQueryParams &
    userQueryParameters &
    timestampQueryParameters &
    searchParams
  ).as(ProjectQueryParameters.apply _)

  def aoiQueryParameters = (
    orgQueryParams & userQueryParameters & timestampQueryParameters
  ).as(AoiQueryParameters.apply _)

  def orgQueryParams = parameters(
    'organization.as(deserializerUUID).*
  ).as(OrgQueryParameters.apply _)

  def ownerQueryParameters = parameters(
    'owner.as[String].?
  ).as(OwnerQueryParameters.apply _)

  def onlyUserQueryParameters = parameters(
    (
    'createdBy.as[String].?,
    'modifiedBy.as[String].?
    )
  ).as(OnlyUserQueryParameters.apply _)

  def userQueryParameters = (
    onlyUserQueryParameters &
    ownerQueryParameters &
    activationParams
  ).as(UserQueryParameters.apply _)

  def timestampQueryParameters = parameters(
    (
      'minCreateDatetime.as(deserializerTimestamp).?,
      'maxCreateDatetime.as(deserializerTimestamp).?,
      'minModifiedDatetime.as(deserializerTimestamp).?,
      'maxModifiedDatetime.as(deserializerTimestamp).?
    )
  ).as(TimestampQueryParameters.apply _)

  def annotationQueryParams = (
    orgQueryParams &
    userQueryParameters &
    parameters((
      'label.as[String].?,
      'machineGenerated.as[Boolean].?,
      'minConfidence.as[Double].?,
      'maxConfidence.as[Double].?,
      'quality.as[String].?
    ))).as(AnnotationQueryParameters.apply _)

  def shapeQueryParams = (
    orgQueryParams & userQueryParameters & timestampQueryParameters
  ).as(ShapeQueryParameters.apply _)

  def searchParams = parameters(
    'search.as[String].?
  ).as(SearchQueryParameters.apply _)

  def activationParams = parameters(
    'isActive.as[Boolean].?
  ).as(ActivationQueryParameters.apply _)

  def platformIdParams = parameters(
    'platformId.as[UUID].?
  ).as(PlatformIdQueryParameters.apply _)
}
