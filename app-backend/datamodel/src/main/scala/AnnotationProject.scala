package com.rasterfoundry.datamodel

import geotrellis.vector.{Geometry, Projected}
import io.circe.generic.semiauto._
import io.circe._

import java.time.Instant
import java.util.UUID

final case class AnnotationProject(
    id: UUID,
    createdAt: Instant,
    createdBy: String,
    name: String,
    projectType: AnnotationProjectType,
    taskSizeMeters: Option[Int],
    aoi: Option[Projected[Geometry]],
    labelersTeamId: Option[UUID],
    validatorsTeamId: Option[UUID],
    projectId: Option[UUID]
)

object AnnotationProject {
  implicit val encAnnotationProject: Encoder[AnnotationProject] = deriveEncoder
  implicit val decAnnotationProject: Decoder[AnnotationProject] = deriveDecoder

  final case class Create(
      name: String,
      projectType: AnnotationProjectType,
      taskSizeMeters: Option[Int],
      aoi: Option[Projected[Geometry]],
      labelersTeamId: Option[UUID],
      validatorsTeamId: Option[UUID],
      projectId: Option[UUID],
      tileLayers: List[TileLayer.Create],
      labelClassGroups: List[AnnotationLabelClassGroup.Create]
  )
}
