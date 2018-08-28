package com.azavea.rf.datamodel

import java.sql.Timestamp
import java.util.UUID

import io.circe._
import io.circe.generic.JsonCodec
import io.circe.syntax._

@JsonCodec
final case class Team(id: UUID,
                      createdAt: java.sql.Timestamp,
                      createdBy: String,
                      modifiedAt: java.sql.Timestamp,
                      modifiedBy: String,
                      organizationId: UUID,
                      name: String,
                      settings: Json,
                      isActive: Boolean)

object Team {
  def tupled = (Team.apply _).tupled

  def create = Create.apply _

  @JsonCodec
  final case class Create(organizationId: UUID,
                          name: String,
                          settings: Json = "{}".asJson) {
    def toTeam(user: User): Team = {
      val id = java.util.UUID.randomUUID()
      val now = new Timestamp(new java.util.Date().getTime)

      Team(
        id,
        now, // createdAt
        user.id, // createdBy
        now, // modifiedAt
        user.id, // modifiedBy
        this.organizationId,
        this.name,
        this.settings,
        isActive = true
      )
    }
  }
}
