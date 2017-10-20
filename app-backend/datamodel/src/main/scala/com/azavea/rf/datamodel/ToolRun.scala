package com.azavea.rf.datamodel

import java.util.UUID
import java.sql.Timestamp

import io.circe._
import io.circe.generic.JsonCodec

@JsonCodec
case class ToolRun(
  id: UUID,
  name: Option[String],
  createdAt: Timestamp,
  createdBy: String,
  modifiedAt: Timestamp,
  modifiedBy: String,
  owner: String,
  visibility: Visibility,
  organizationId: UUID,
  executionParameters: Json
)

object ToolRun {
  def create = Create.apply _
  def tupled = (ToolRun.apply _).tupled

  @JsonCodec
  case class Create(
    name: Option[String],
    visibility: Visibility,
    organizationId: UUID,
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
        user.id,
        ownerId,
        visibility,
        organizationId,
        executionParameters
      )
    }
  }
}
