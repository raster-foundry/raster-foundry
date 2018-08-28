package com.azavea.rf.datamodel

import java.sql.Timestamp
import java.util.UUID

import io.circe.generic.JsonCodec

@JsonCodec
final case class UserGroupRole(id: UUID,
                               createdAt: Timestamp,
                               createdBy: String,
                               modifiedAt: Timestamp,
                               modifiedBy: String,
                               isActive: Boolean,
                               userId: String,
                               groupType: GroupType,
                               groupId: UUID,
                               groupRole: GroupRole,
                               membershipStatus: MembershipStatus)

object UserGroupRole {
  def create = Create.apply _
  def tupled = (UserGroupRole.apply _).tupled

  final case class UserGroup(userId: String,
                             groupType: GroupType,
                             groupId: UUID)

  @JsonCodec
  final case class UserRole(userId: String, groupRole: GroupRole)

  @JsonCodec
  final case class Create(userId: String,
                          groupType: GroupType,
                          groupId: UUID,
                          groupRole: GroupRole) {
    def toUserGroupRole(creator: User,
                        membershipStatus: MembershipStatus): UserGroupRole = {
      val now = new Timestamp(new java.util.Date().getTime)
      UserGroupRole(
        UUID.randomUUID(),
        now, // createdAt
        creator.id, // createdBy
        now, // modifiedAt
        creator.id, // modifiedBy
        isActive = true,
        userId, // user that is being given the group role
        groupType,
        groupId,
        groupRole,
        membershipStatus
      )
    }
  }
}
