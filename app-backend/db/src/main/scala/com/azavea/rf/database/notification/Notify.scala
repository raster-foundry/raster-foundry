package com.azavea.rf.database.notification

import com.azavea.rf.common.RollbarNotifier
import com.azavea.rf.common.notification.Email.NotificationEmail
import com.azavea.rf.database._
import com.azavea.rf.database.notification.templates._
import com.azavea.rf.datamodel._


import cats.implicits._
import doobie.ConnectionIO
import doobie.implicits._

import org.apache.commons.mail.{Email, EmailException}

import java.util.UUID

object Notify extends RollbarNotifier {
  def sendEmail(publicSettings: Platform.PublicSettings, privateSettings: Platform.PrivateSettings, to: String, subject: String, messageRich: String, messagePlain: String):
      ConnectionIO[Unit] = {
    val email = new NotificationEmail
    for {
      _ <- publicSettings.emailUser match {
        case "" => ().pure[ConnectionIO]
        case s => {
          val preparedEmail = email.setEmail(
            publicSettings.emailSmtpHost, publicSettings.emailSmtpPort, publicSettings.emailSmtpEncryption,
            publicSettings.emailUser, privateSettings.emailPassword,
            to, subject, messageRich, messagePlain
          )
          try {
            preparedEmail.map({ (configuredEmail: Email) => configuredEmail.send }).pure[ConnectionIO]
          } catch {
            case e: EmailException => sendError(e).pure[ConnectionIO]
          }
        }
      }
      // Only attempt to send the email if the platform has configured its email settings
    } yield { () }
  }

  def sendGroupNotification(
    platformId: UUID, groupId: UUID, groupType: GroupType, initiatorId: String, subjectId: String, messageType: MessageType
  ): ConnectionIO[Unit] =
    for {
      platform <- PlatformDao.unsafeGetPlatformById(platformId)
      publicSettings = platform.publicSettings
      privateSettings = platform.privateSettings
      platformHost = publicSettings.platformHost.getOrElse("app.rasterfoundry.com")
      _ <- logger.debug("Constructing message").pure[ConnectionIO]
      emailData <- messageType match {
        case MessageType.GroupRequest =>
          PlainGroupRequest(groupId, groupType, initiatorId, platform).build
        case MessageType.GroupInvitation =>
          PlainGroupInvitation(groupId, groupType, initiatorId, platform).build
      }
      _ <- logger.debug("Fetching users").pure[ConnectionIO]
      users <- messageType match {
        case MessageType.GroupRequest =>
          UserGroupRoleDao.listByGroupAndRole(groupType, groupId, GroupRole.Admin) flatMap {
            (userGroupRoles: List[UserGroupRole]) => UserDao.getUsersByIds(userGroupRoles.map((ugr: UserGroupRole) => ugr.userId))
          }
        case MessageType.GroupInvitation =>
          UserDao.unsafeGetUserById(subjectId).map((usr: User) => List(usr))
      }
      emails = users map { _.email }
      _ <- logger.debug(s"Sending emails to ${users.length} admins").pure[ConnectionIO]
      result <- emails.map(
        sendEmail(publicSettings, privateSettings, _,
                  emailData.subject, emailData.richBody, emailData.plainBody)
      ).sequence
    } yield { () }
}
