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
    execution_parameters: Json
  ) {
    def toToolRun(userId: String): ToolRun = {
      val now = new Timestamp((new java.util.Date).getTime)
      ToolRun(
        UUID.randomUUID,
        now,
        userId,
        now,
        userId,
        visibility,
        organizationId,
        project,
        tool,
        execution_parameters
      )
    }
  }
}
