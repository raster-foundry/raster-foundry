package com.azavea.rf.datamodel

import java.util.UUID
import java.sql.Timestamp

import io.circe._
import io.circe.generic.JsonCodec
import io.circe.syntax._

@JsonCodec
case class UserGroupRole(
    id: UUID,
    createdAt: Timestamp,
    createdBy: String,
    modifiedAt: Timestamp,
    modifiedBy: String,
    isActive: Boolean,
    userId: String,
    groupType: GroupType,
    groupId: UUID,
    groupRole: GroupRole
)

object UserGroupRole {
    def create = Create.apply _
    def tupled = (UserGroupRole.apply _).tupled

    @JsonCodec
    case class Create(
        userToAdd: User,
        groupType: GroupType,
        groupId: UUID,
        groupRole: GroupRole,
        creator: User
    ) {
        def toUserGroupRole: UserGroupRole = {
            val now = new Timestamp((new java.util.Date()).getTime())
            UserGroupRole(
                UUID.randomUUID(),
                now, // createdAt
                creator.id, // createdBy
                now, // modifiedAt
                creator.id, // modifiedBy
                true, // always default isActive to true
                userToAdd.id, // user that is being given the group role
                groupType,
                groupId,
                groupRole
            )
        }
    }
}
