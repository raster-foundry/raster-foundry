package com.rasterfoundry.datamodel

import geotrellis.vector.{Geometry, Projected}
import io.circe._
import io.circe.generic.JsonCodec

import java.sql.Timestamp
import java.util.UUID

@JsonCodec
final case class ToolRun(
    id: UUID,
    name: Option[String],
    createdAt: Timestamp,
    createdBy: String,
    modifiedAt: Timestamp,
    owner: String,
    visibility: Visibility,
    projectId: Option[UUID],
    projectLayerId: Option[UUID],
    templateId: Option[UUID],
    executionParameters: Json
)

object ToolRun {
  def create = Create.apply _
  def tupled = (ToolRun.apply _).tupled

  @JsonCodec
  final case class Create(
      name: Option[String],
      visibility: Visibility,
      projectId: Option[UUID],
      projectLayerId: Option[UUID],
      templateId: Option[UUID],
      executionParameters: Json,
      owner: Option[String]
  ) extends OwnerCheck {
    def toToolRun(user: User): ToolRun = {

      val now = new Timestamp((new java.util.Date).getTime)

      val ownerId = checkOwner(user, this.owner)

      ToolRun(
        UUID.randomUUID,
        name,
        now,
        user.id,
        now,
        ownerId,
        visibility,
        projectId,
        projectLayerId,
        templateId,
        executionParameters
      )
    }
  }
}

@JsonCodec
final case class ToolRunWithRelated(
    id: UUID,
    name: Option[String],
    createdAt: Timestamp,
    createdBy: String,
    modifiedAt: Timestamp,
    owner: String,
    visibility: Visibility,
    projectId: Option[UUID],
    projectLayerId: Option[UUID],
    templateId: Option[UUID],
    executionParameters: Json,
    templateTitle: String,
    layerColorGroupHex: String,
    layerGeometry: Option[Projected[Geometry]]
)
