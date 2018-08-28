package com.azavea.rf.datamodel

import java.sql.Timestamp
import java.util.UUID

import io.circe.generic.JsonCodec

@JsonCodec
final case class MapToken(id: UUID,
                          createdAt: Timestamp,
                          createdBy: String,
                          modifiedAt: Timestamp,
                          modifiedBy: String,
                          owner: String,
                          name: String,
                          project: Option[UUID],
                          toolRun: Option[UUID])

object MapToken {
  def tupled = (MapToken.apply _).tupled

  def create = Create.apply _

  @JsonCodec
  final case class Create(name: String,
                          project: Option[UUID],
                          toolRun: Option[UUID],
                          owner: Option[String])
      extends OwnerCheck {
    def toMapToken(user: User): MapToken = {

      val id = java.util.UUID.randomUUID()
      val now = new Timestamp(new java.util.Date().getTime)
      val ownerId = checkOwner(user, this.owner)

      MapToken(
        id,
        now,
        user.id,
        now,
        user.id,
        ownerId,
        this.name,
        this.project,
        this.toolRun
      )
    }
  }
}
