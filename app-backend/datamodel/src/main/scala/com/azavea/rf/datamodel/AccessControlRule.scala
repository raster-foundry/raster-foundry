package com.azavea.rf.datamodel

import java.util.UUID
import java.sql.Timestamp

import io.circe._
import io.circe.generic.JsonCodec
import io.circe.syntax._

@JsonCodec
case class AccessControlRule(
  id: UUID,
  createdAt: Timestamp,
  createdBy: String,
  isActive: Boolean,
  objectType: ObjectType,
  objectId: UUID,
  subjectType: SubjectType,
  subjectId: Option[String],
  actionType: ActionType
)

object AccessControlRule {
  def create = Create.apply _
  def tupled = (AccessControlRule.apply _).tupled

  @JsonCodec
  case class Create(
    isActive: Boolean,
    subjectType: SubjectType,
    subjectId: Option[String],
    actionType: ActionType
  ) {
    def toAccessControlRule(user: User, objectType: ObjectType, objectId: UUID): AccessControlRule = {
      val now = new Timestamp((new java.util.Date()).getTime())
      AccessControlRule(
        UUID.randomUUID(),
        now, // createdAt
        user.id, // createdBy
        isActive,
        objectType,
        objectId,
        subjectType,
        subjectId,
        actionType
      )
    }
  }
}
