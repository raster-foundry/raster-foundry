package com.rasterfoundry.database.notification

import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.common.notification.Email.{
  EmailConfig,
  NotificationEmail
}
import com.rasterfoundry.database._
import com.rasterfoundry.database.notification.templates._
import com.rasterfoundry.datamodel._

import cats.implicits._
import doobie.ConnectionIO
import doobie.implicits._
import org.apache.commons.mail.{Email, EmailException}

import java.util.UUID

object Notify extends RollbarNotifier {
  def sendEmail(publicSettings: Platform.PublicSettings,
                privateSettings: Platform.PrivateSettings,
                to: String,
                subject: String,
                messageRich: String,
                messagePlain: String): ConnectionIO[Unit] = {
    val email = new NotificationEmail
    for {
      _ <- publicSettings.emailSmtpUserName match {
        case "" => ().pure[ConnectionIO]
        case _ => {
          val preparedEmail = email.setEmail(
            EmailConfig(
              publicSettings.emailSmtpHost,
              publicSettings.emailSmtpPort,
              publicSettings.emailSmtpEncryption,
              publicSettings.emailSmtpUserName,
              privateSettings.emailPassword
            ),
            to,
            subject,
            messageRich,
            messagePlain,
            publicSettings.emailFrom,
            publicSettings.emailFromDisplayName
          )
          try {
            preparedEmail
              .map({ (configuredEmail: Email) =>
                configuredEmail.send
              })
              .pure[ConnectionIO]
          } catch {
            case e: EmailException => sendError(e).pure[ConnectionIO]
          }
        }
      }
      // Only attempt to send the email if the platform has configured its email settings
    } yield { () }
  }

  def sendNotification(platformId: UUID,
                       messageType: MessageType,
                       builder: MessageType => ConnectionIO[EmailData],
                       userFinder: MessageType => ConnectionIO[List[User]])
    : ConnectionIO[Unit] = {
    for {
      platform <- PlatformDao.unsafeGetPlatformById(platformId)
      publicSettings = platform.publicSettings
      privateSettings = platform.privateSettings
      emailData <- builder(messageType)
      _ <- logger.debug("Fetching users").pure[ConnectionIO]
      recipients <- userFinder(messageType)
      emails = recipients map { user =>
        (user.personalInfo.email, user.email) match {
          case ("", loginEmail)  => loginEmail
          case (contactEmail, _) => contactEmail
        }
      }
      _ <- logger
        .debug(s"Sending emails to ${recipients.length} admins")
        .pure[ConnectionIO]
      _ <- emails
        .map(
          sendEmail(publicSettings,
                    privateSettings,
                    _,
                    emailData.subject,
                    emailData.richBody,
                    emailData.plainBody)
        )
        .sequence
    } yield { () }
  }
}
