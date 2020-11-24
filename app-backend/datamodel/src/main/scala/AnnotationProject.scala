package com.rasterfoundry.datamodel

import geotrellis.vector.{Geometry, Projected}
import io.circe._
import io.circe.generic.semiauto._

import java.sql.Timestamp
import java.util.UUID

final case class AnnotationProject(
    id: UUID,
    createdAt: Timestamp,
    createdBy: String,
    name: String,
    projectType: AnnotationProjectType,
    taskSizeMeters: Option[Double],
    taskSizePixels: Int,
    aoi: Option[Projected[Geometry]],
    labelersTeamId: Option[UUID],
    validatorsTeamId: Option[UUID],
    projectId: Option[UUID],
    status: AnnotationProjectStatus,
    taskStatusSummary: Option[Map[String, Int]] = None,
    campaignId: Option[UUID] = None,
    capturedAt: Option[Timestamp] = None
) {
  def withRelated(
      tileLayers: List[TileLayer],
      labelClassGroups: List[AnnotationLabelClassGroup.WithLabelClasses]
  ): AnnotationProject.WithRelated =
    AnnotationProject.WithRelated(
      id,
      createdAt,
      createdBy,
      name,
      projectType,
      taskSizeMeters,
      taskSizePixels,
      aoi,
      labelersTeamId,
      validatorsTeamId,
      projectId,
      status,
      tileLayers,
      labelClassGroups,
      taskStatusSummary,
      campaignId,
      capturedAt
    )
}

object AnnotationProject {
  implicit val encAnnotationProject: Encoder[AnnotationProject] = deriveEncoder
  implicit val decAnnotationProject: Decoder[AnnotationProject] = deriveDecoder

  final case class Create(
      name: String,
      projectType: AnnotationProjectType,
      taskSizePixels: Int,
      aoi: Option[Projected[Geometry]],
      labelersTeamId: Option[UUID],
      validatorsTeamId: Option[UUID],
      projectId: Option[UUID],
      tileLayers: List[TileLayer.Create],
      labelClassGroups: List[AnnotationLabelClassGroup.Create],
      status: AnnotationProjectStatus,
      campaignId: Option[UUID] = None,
      capturedAt: Option[Timestamp] = None
  )

  object Create {
    implicit val decCreate: Decoder[Create] = deriveDecoder
  }

  final case class WithRelated(
      id: UUID,
      createdAt: Timestamp,
      createdBy: String,
      name: String,
      projectType: AnnotationProjectType,
      taskSizeMeters: Option[Double],
      taskSizePixels: Int,
      aoi: Option[Projected[Geometry]],
      labelersTeamId: Option[UUID],
      validatorsTeamId: Option[UUID],
      projectId: Option[UUID],
      status: AnnotationProjectStatus,
      tileLayers: List[TileLayer],
      labelClassGroups: List[AnnotationLabelClassGroup.WithLabelClasses],
      taskStatusSummary: Option[Map[String, Int]] = None,
      campaignId: Option[UUID] = None,
      capturedAt: Option[Timestamp] = None
  ) {
    def toProject =
      AnnotationProject(
        id,
        createdAt,
        createdBy,
        name,
        projectType,
        taskSizeMeters,
        taskSizePixels,
        aoi,
        labelersTeamId,
        validatorsTeamId,
        projectId,
        status,
        taskStatusSummary,
        campaignId,
        capturedAt
      )

    def withSummary(
        labelClassSummary: List[AnnotationProject.LabelClassGroupSummary]
    ) =
      AnnotationProject.WithRelatedAndLabelClassSummary(
        id,
        createdAt,
        createdBy,
        name,
        projectType,
        taskSizeMeters,
        taskSizePixels,
        aoi,
        labelersTeamId,
        validatorsTeamId,
        projectId,
        status,
        tileLayers,
        labelClassGroups,
        taskStatusSummary,
        labelClassSummary,
        campaignId,
        capturedAt
      )
  }

  object WithRelated {
    implicit val encRelated: Encoder[WithRelated] = deriveEncoder
    implicit val decRelated: Decoder[WithRelated] = deriveDecoder
  }

  final case class LabelClassSummary(
      labelClassId: UUID,
      labelClassName: String,
      count: Int
  )

  object LabelClassSummary {
    implicit val encLabelClassGroupSummary: Encoder[LabelClassSummary] =
      deriveEncoder
  }

  final case class LabelClassGroupSummary(
      labelClassGroupId: UUID,
      labelClassGroupName: String,
      labelClassSummaries: List[LabelClassSummary]
  )

  object LabelClassGroupSummary {
    implicit val encLabelClassGroupSummary: Encoder[LabelClassGroupSummary] =
      deriveEncoder
  }

  final case class WithRelatedAndLabelClassSummary(
      id: UUID,
      createdAt: Timestamp,
      createdBy: String,
      name: String,
      projectType: AnnotationProjectType,
      taskSizeMeters: Option[Double],
      taskSizePixels: Int,
      aoi: Option[Projected[Geometry]],
      labelersTeamId: Option[UUID],
      validatorsTeamId: Option[UUID],
      projectId: Option[UUID],
      status: AnnotationProjectStatus,
      tileLayers: List[TileLayer],
      labelClassGroups: List[AnnotationLabelClassGroup.WithLabelClasses],
      taskStatusSummary: Option[Map[String, Int]] = None,
      labelClassSummary: List[LabelClassGroupSummary],
      campaignId: Option[UUID] = None,
      capturedAt: Option[Timestamp] = None
  ) {
    def toProject =
      AnnotationProject(
        id,
        createdAt,
        createdBy,
        name,
        projectType,
        taskSizeMeters,
        taskSizePixels,
        aoi,
        labelersTeamId,
        validatorsTeamId,
        projectId,
        status,
        taskStatusSummary,
        campaignId,
        capturedAt
      )
  }

  object WithRelatedAndLabelClassSummary {
    implicit val encRelatedAndSummary
      : Encoder[WithRelatedAndLabelClassSummary] =
      deriveEncoder
  }
}
