package com.azavea.rf.database.notification

import java.util.UUID

import cats.implicits._
import com.azavea.rf.database._
import com.azavea.rf.database.notification.templates._
import com.azavea.rf.datamodel._
import doobie.ConnectionIO

final case class GroupNotifier(
    platformId: UUID,
    groupId: UUID,
    groupType: GroupType,
    initiatorId: String,
    subjectId: String,
    messageType: MessageType
) extends Notifier {
  def builder(messageType: MessageType): ConnectionIO[EmailData] = {
    messageType match {
      case MessageType.GroupRequest =>
        PlainGroupRequest(groupId, groupType, initiatorId, platformId).build
      case MessageType.GroupInvitation =>
        PlainGroupInvitation(groupId, groupType, initiatorId, platformId).build
      case _ =>
        throw new Exception(
          s"Tried to send a group request message with invalid message type ${messageType}")
    }
  }

  def userFinder(messageType: MessageType): ConnectionIO[List[User]] = {
    messageType match {
      case MessageType.GroupRequest =>
        UserGroupRoleDao.listByGroupAndRole(groupType, groupId, GroupRole.Admin) flatMap {
          userGroupRoles: List[UserGroupRole] =>
            UserDao.getUsersByIds(
              userGroupRoles.map((ugr: UserGroupRole) => ugr.userId)
            )
        }
      case MessageType.GroupInvitation =>
        UserDao.unsafeGetUserById(subjectId).map((usr: User) => List(usr))
      case _ =>
        throw new Exception(
          s"Tried to send a group request message with invalid message type ${messageType}")
    }
  }

  def send: ConnectionIO[Either[Throwable, Unit]] =
    Notify
      .sendNotification(platformId, messageType, builder, userFinder)
      .attempt
}
