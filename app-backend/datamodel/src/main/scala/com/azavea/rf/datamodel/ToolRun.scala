package com.azavea.rf.datamodel

import java.util.UUID
import java.sql.Timestamp

import io.circe._
import io.circe.generic.JsonCodec

@JsonCodec
case class ToolRun(
  id: UUID,
  createdAt: Timestamp,
  createdBy: String,
  modifiedAt: Timestamp,
  modifiedBy: String,
  owner: String,
  visibility: Visibility,
  organizationId: UUID,
  project: UUID,
  tool: UUID,
  execution_parameters: Json
)

object ToolRun {
  def create = Create.apply _
  def tupled = (ToolRun.apply _).tupled

  @JsonCodec
  case class Create(
    visibility: Visibility,
    organizationId: UUID,
    project: UUID,
    tool: UUID,
    execution_parameters: Json,
    owner: Option[String]
  ) extends OwnerCheck {
    def toToolRun(user: User): ToolRun = {
      val now = new Timestamp((new java.util.Date).getTime)

      val ownerId = checkOwner(user, this.owner)

      ToolRun(
        UUID.randomUUID,
        now,
        user.id,
        now,
        user.id,
        ownerId,
        visibility,
        organizationId,
        project,
        tool,
        execution_parameters
      )
    }
  }
}
