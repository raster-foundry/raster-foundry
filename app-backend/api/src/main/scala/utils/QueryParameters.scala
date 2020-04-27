package com.rasterfoundry.api.utils.queryparams

import com.rasterfoundry.datamodel._

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters
import akka.http.scaladsl.unmarshalling._
import javax.xml.bind.DatatypeConverter

import java.sql.Timestamp
import java.util.UUID

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

  implicit val deserializerTaskStatus: Unmarshaller[String, TaskStatus] =
    Unmarshaller.strict[String, TaskStatus] { s =>
      TaskStatus.fromString(s)
    }

  implicit val deserializerAnnotationProjectType
    : Unmarshaller[String, AnnotationProjectType] =
    Unmarshaller.strict[String, AnnotationProjectType] { s =>
      AnnotationProjectType.fromString(s)
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
        tagQueryParameters &
        parameters(
          'analysisId.as[UUID].?
        )
    ).as(ProjectQueryParameters.apply _)

  def orgQueryParams =
    parameters(
      'organization.as(deserializerUUID).*
    ).as(OrgQueryParameters.apply _)

  def ownerQueryParameters =
    parameters(
      'owner.as[String].*
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
        'createdBy.as[String].?
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
          'bbox.as[String].*,
          'withOwnerInfo.as[Boolean].?,
          'taskId.as[UUID].?
        )
      )).as(AnnotationQueryParameters.apply _)

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

  def annotationExportQueryParameters =
    parameters(
      'exportAll.as[Boolean].?
    ).as(AnnotationExportQueryParameters.apply _)

  def taskQueryParameters =
    parameters(
      'status.as[TaskStatus].?,
      'locked.as[Boolean].?,
      'lockedBy.as[String].?,
      'bbox.as[String].*,
      'actionUser.as[String].?,
      'actionType.as[TaskStatus].?,
      'actionStartTime.as(deserializerTimestamp).?,
      'actionEndTime.as(deserializerTimestamp).?,
      'actionMinCount.as[Int].?,
      'actionMaxCount.as[Int].?,
      'format.as[String].?
    ).as(TaskQueryParameters.apply _)

  def userTaskActivityParameters =
    parameters(
      'actionStartTime.as(deserializerTimestamp).?,
      'actionEndTime.as(deserializerTimestamp).?,
      'actionUser.as[String].?
    ).as(UserTaskActivityParameters.apply _)

  def stacExportQueryParameters =
    (
      userAuditQueryParameters &
        ownerQueryParameters &
        searchParams &
        parameters(
          'exportStatus.as[String].?,
          'annotationProjectId.as[UUID].?
        )
    ).as(StacExportQueryParameters.apply _)

  def annotationProjectFilterParameters =
    parameters(
      (
        'projectType.as(deserializerAnnotationProjectType).?,
        'taskStatusesInclude.as(deserializerTaskStatus).*
      )
    ).as(AnnotationProjectFilterQueryParameters.apply _)

  def annotationProjectQueryParameters =
    (
      ownerQueryParameters &
        searchParams &
        ownershipTypeQueryParameters &
        groupQueryParameters &
        annotationProjectFilterParameters &
        parameters(
          'campaignId.as[UUID].?
        )
    ).as(AnnotationProjectQueryParameters.apply _)

  def campaignQueryParameters =
    (
      ownerQueryParameters &
        searchParams &
        ownershipTypeQueryParameters &
        groupQueryParameters &
        parameters(
          'campaignType.as(deserializerAnnotationProjectType).?
        )
    ).as(CampaignQueryParameters.apply _)
}
