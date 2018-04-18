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
    modifiedAt: Timestamp,
    modifiedBy: String,
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

    case class Create(
        objectType: ObjectType,
        objectId: UUID,
        subjectType: SubjectType,
        subjectId: Option[String],
        actionType: ActionType,
        user: User
    ) {
        def toAccessControlRule: AccessControlRule = {
            val now = new Timestamp((new java.util.Date()).getTime())
            AccessControlRule(
                UUID.randomUUID(),
                now, // createdAt
                user.id, // createdBy
                now, // modifiedAt
                user.id, // modifiedBy
                true, // isActive
                objectType,
                objectId,
                subjectType,
                subjectId,
                actionType
            )
        }
    }
}
