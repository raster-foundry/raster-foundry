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

  case class UserGroup(
    userId: String,
    groupType: GroupType,
    groupId: UUID
  )

  @JsonCodec
  case class UserRole(
    userId: String,
    groupRole: GroupRole
  )

  @JsonCodec
  case class Create(
    userId: String,
    groupType: GroupType,
    groupId: UUID,
    groupRole: GroupRole
  ) {
    def toUserGroupRole(creator: User): UserGroupRole = {
      val now = new Timestamp((new java.util.Date()).getTime())
      UserGroupRole(
        UUID.randomUUID(),
        now, // createdAt
        creator.id, // createdBy
        now, // modifiedAt
        creator.id, // modifiedBy
        true, // always default isActive to true
        userId, // user that is being given the group role
        groupType,
        groupId,
        groupRole
      )
    }
  }
}
