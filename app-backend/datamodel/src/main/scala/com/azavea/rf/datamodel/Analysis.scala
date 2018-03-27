package com.azavea.rf.datamodel

import java.util.UUID
import java.sql.Timestamp

import io.circe._
import io.circe.generic.JsonCodec

@JsonCodec
case class Analysis(
  id: UUID,
  name: String,
  createdAt: Timestamp,
  createdBy: String,
  modifiedAt: Timestamp,
  modifiedBy: String,
  owner: String,
  visibility: Visibility,
  organizationId: UUID,
  executionParameters: Json,
  readonly: Boolean
)

object Analysis {
  @JsonCodec
  case class Create(
    name: Option[String],
    visibility: Visibility,
    organizationId: UUID,
    executionParameters: Json,
    owner: Option[String],
    readonly: Option[Boolean]
  ) extends OwnerCheck {
    def toAnalysis(user: User): Analysis = {

      val now = new Timestamp((new java.util.Date).getTime)

      val ownerId = checkOwner(user, this.owner)

      Analysis(
        UUID.randomUUID,
        name.getOrElse(""),
        now,
        user.id,
        now,
        user.id,
        ownerId,
        visibility,
        organizationId,
        executionParameters,
        readonly.getOrElse(false)
      )
    }
  }
}
