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
    capturedAt: Option[Timestamp] = None,
    isActive: Boolean
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
      capturedAt,
      isActive
    )
}

object AnnotationProject {
  implicit val encAnnotationProject: Encoder[AnnotationProject] = deriveEncoder
  implicit val decAnnotationProject: Decoder[AnnotationProject] =
    Decoder.forProduct16(
      "id",
      "createdAt",
      "createdBy",
      "name",
      "projectType",
      "taskSizeMeters",
      "taskSizePixels",
      "aoi",
      "labelersTeamId",
      "validatorsTeamId",
      "projectId",
      "status",
      "taskStatusSummary",
      "campaignId",
      "capturedAt",
      "isActive"
    )(
      (
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
          taskStatusSummary: Option[Map[String, Int]],
          campaignId: Option[UUID],
          capturedAt: Option[Timestamp],
          isActive: Option[Boolean]
      ) =>
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
          capturedAt,
          isActive getOrElse true
        )
    )

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
      capturedAt: Option[Timestamp] = None,
      isActive: Boolean
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
        capturedAt,
        isActive
      )

    def withSummary(
        labelClassSummary: List[LabelClassGroupSummary]
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
        capturedAt,
        isActive
      )
  }

  object WithRelated {
    implicit val encRelated: Encoder[WithRelated] = deriveEncoder
    implicit val decRelated: Decoder[WithRelated] =
      Decoder.forProduct18(
        "id",
        "createdAt",
        "createdBy",
        "name",
        "projectType",
        "taskSizeMeters",
        "taskSizePixels",
        "aoi",
        "labelersTeamId",
        "validatorsTeamId",
        "projectId",
        "status",
        "tileLayers",
        "labelClassGroups",
        "taskStatusSummary",
        "campaignId",
        "capturedAt",
        "isActive"
      )(
        (
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
            taskStatusSummary: Option[Map[String, Int]],
            campaignId: Option[UUID],
            capturedAt: Option[Timestamp],
            isActive: Option[Boolean]
        ) =>
          WithRelated(
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
            capturedAt,
            isActive getOrElse true
          )
      )
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
      capturedAt: Option[Timestamp] = None,
      isActive: Boolean
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
        capturedAt,
        isActive
      )
  }

  object WithRelatedAndLabelClassSummary {
    implicit val encRelatedAndSummary
        : Encoder[WithRelatedAndLabelClassSummary] =
      deriveEncoder
  }
}
