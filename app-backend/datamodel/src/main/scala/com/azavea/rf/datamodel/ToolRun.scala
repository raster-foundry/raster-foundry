package com.azavea.rf.datamodel

import java.sql.Timestamp
import java.util.UUID

import io.circe._
import io.circe.generic.JsonCodec

@JsonCodec
final case class ToolRun(id: UUID,
                         name: Option[String],
                         createdAt: Timestamp,
                         createdBy: String,
                         modifiedAt: Timestamp,
                         modifiedBy: String,
                         owner: String,
                         visibility: Visibility,
                         executionParameters: Json)

object ToolRun {
  def create = Create.apply _
  def tupled = (ToolRun.apply _).tupled

  @JsonCodec
  final case class Create(name: Option[String],
                          visibility: Visibility,
                          executionParameters: Json,
                          owner: Option[String])
      extends OwnerCheck {
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
        executionParameters
      )
    }
  }
}
