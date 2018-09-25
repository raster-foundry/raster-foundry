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

  implicit val deserializerUUID: Unmarshaller[String, UUID] =
    Unmarshaller.strict[String, UUID] { s =>
      UUID.fromString(s)
    }

  implicit val deserializerTimestamp: Unmarshaller[String, Timestamp] =
    Unmarshaller.strict[String, Timestamp] { s =>
      Timestamp.from(DatatypeConverter.parseDateTime(s).getTime().toInstant())
    }

  implicit val deserializerGroupType: Unmarshaller[String, GroupType] =
    Unmarshaller.strict[String, GroupType] { s =>
      GroupType.fromString(s)
    }

}

trait QueryParametersCommon extends QueryParameterDeserializers {
  def projectQueryParameters =
    (
      orgQueryParams &
        userQueryParameters &
        timestampQueryParameters &
        searchParams &
        ownershipTypeQueryParameters &
        groupQueryParameters &
        tagQueryParameters
    ).as(ProjectQueryParameters.apply _)

  def aoiQueryParameters =
    (
      orgQueryParams & userQueryParameters & timestampQueryParameters
    ).as(AoiQueryParameters.apply _)

  def orgQueryParams =
    parameters(
      'organization.as(deserializerUUID).*
    ).as(OrgQueryParameters.apply _)

  def ownerQueryParameters =
    parameters(
      'owner.as[String].?
    ).as(OwnerQueryParameters.apply _)

  def ownershipTypeQueryParameters =
    parameters(
      'ownershipType.as[String].?
    ).as(OwnershipTypeQueryParameters.apply _)

  def groupQueryParameters =
    parameters(
      (
        'groupType.as(deserializerGroupType).?,
        'groupId.as(deserializerUUID).?
      )
    ).as(GroupQueryParameters.apply _)

  def userAuditQueryParameters =
    parameters(
      (
        'createdBy.as[String].?,
        'modifiedBy.as[String].?
      )
    ).as(UserAuditQueryParameters.apply _)

  def userQueryParameters =
    (
      userAuditQueryParameters &
        ownerQueryParameters &
        activationParams
    ).as(UserQueryParameters.apply _)

  def timestampQueryParameters =
    parameters(
      (
        'minCreateDatetime.as(deserializerTimestamp).?,
        'maxCreateDatetime.as(deserializerTimestamp).?,
        'minModifiedDatetime.as(deserializerTimestamp).?,
        'maxModifiedDatetime.as(deserializerTimestamp).?
      )
    ).as(TimestampQueryParameters.apply _)

  def annotationQueryParams =
    (orgQueryParams &
      userQueryParameters &
      parameters(
        (
          'label.as[String].?,
          'machineGenerated.as[Boolean].?,
          'minConfidence.as[Double].?,
          'maxConfidence.as[Double].?,
          'quality.as[String].?,
          'annotationGroup.as[UUID].?,
          'bbox.as[String].*
        ))).as(AnnotationQueryParameters.apply _)

  def shapeQueryParams =
    (
      orgQueryParams &
        userQueryParameters &
        timestampQueryParameters &
        ownershipTypeQueryParameters &
        groupQueryParameters &
        searchParams
    ).as(ShapeQueryParameters.apply _)

  def searchParams =
    parameters(
      'search.as[String].?
    ).as(SearchQueryParameters.apply _)

  def activationParams =
    parameters(
      'isActive.as[Boolean].?
    ).as(ActivationQueryParameters.apply _)

  def platformIdParams =
    parameters(
      'platformId.as[UUID].?
    ).as(PlatformIdQueryParameters.apply _)

  def tagQueryParameters =
    parameters(
      'tagsInclude.as[String].*,
      'tagsExclude.as[String].*
    ).as(TagQueryParameters.apply _)

  def teamQueryParameters =
    (
      timestampQueryParameters &
        orgQueryParams &
        userAuditQueryParameters &
        searchParams &
        activationParams
    ).as(TeamQueryParameters.apply _)
}
