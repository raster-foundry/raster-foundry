package com.azavea.rf.datamodel

import java.util.UUID

import java.sql.Timestamp

/**
  * Created by cbrown on 3/11/17.
  */
case class MapToken(
  id: UUID,
  createdAt: Timestamp,
  createdBy: String,
  modifiedAt: Timestamp,
  modifiedBy: String,
  organizationId: UUID,
  name: String,
  project: UUID
)


object MapToken {
  def tupled = (MapToken.apply _).tupled

  def create = Create.apply _

  case class Create(
    organizationId: UUID,
    name: String,
    project: UUID
  ) {
    def toMapToken(userId: String): MapToken = {
      val id = java.util.UUID.randomUUID()
      val token = java.util.UUID.randomUUID()
      val now = new Timestamp((new java.util.Date()).getTime())
      MapToken(
        id,
        now,
        userId,
        now,
        userId,
        this.organizationId,
        this.name,
        this.project
      )
    }
  }
}
