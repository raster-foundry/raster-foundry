package com.rasterfoundry.database.notification

import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.database._
import com.rasterfoundry.database.notification.templates._
import com.rasterfoundry.datamodel._
import com.rasterfoundry.notification.email._
import com.rasterfoundry.notification.email.Model._

import cats.effect.{IO, LiftIO}
import cats.implicits._
import doobie.ConnectionIO
import doobie.implicits._

import java.util.UUID

object Notify extends RollbarNotifier {

  val notifier = new LiveEmailNotifier[IO]

  def emailConfigFromPlatformSettings(
      publicSettings: Platform.PublicSettings,
      privateSettings: Platform.PrivateSettings
  ): Either[String, EmailConfig] =
    EncryptionScheme.fromStringE(publicSettings.emailSmtpEncryption) map {
      encryption =>
        EmailConfig(
          EmailHost(publicSettings.emailSmtpHost),
          EmailPort(publicSettings.emailSmtpPort),
          encryption,
          EmailUserName(publicSettings.emailSmtpUserName),
          EmailPassword(privateSettings.emailPassword)
        )
    }

  def sendEmail(
      publicSettings: Platform.PublicSettings,
      privateSettings: Platform.PrivateSettings,
      to: String,
      subject: String,
      messageRich: String,
      messagePlain: String
  ): IO[Unit] = {
    val configE: Either[String, EmailConfig] =
      emailConfigFromPlatformSettings(publicSettings, privateSettings)
    val emailSettingsE: Either[String, EmailSettings] = configE flatMap {
      config =>
        notifier.validateEmailSettings(
          config,
          FromEmailAddress(publicSettings.emailFrom),
          ToEmailAddress(to)
        )
    }

    for {
      emailE <- emailSettingsE traverse { settings =>
        notifier.buildEmail(
          settings,
          FromEmailDisplayName(publicSettings.emailFromDisplayName),
          Subject(subject),
          HtmlBody(messageRich),
          PlainBody(messagePlain)
        )
      }
      _ <- emailE traverse { msg =>
        notifier.sendEmail(msg)
      }
    } yield { () }
  }

  def sendNotification(
      platformId: UUID,
      messageType: MessageType,
      builder: MessageType => ConnectionIO[EmailData],
      userFinder: MessageType => ConnectionIO[List[User]]
  ): ConnectionIO[Unit] = {
    for {
      platform <- PlatformDao.unsafeGetPlatformById(platformId)
      publicSettings = platform.publicSettings
      privateSettings = platform.privateSettings
      emailData <- builder(messageType)
      _ <- logger.debug("Fetching users").pure[ConnectionIO]
      recipients <- userFinder(messageType)
      emailAddresses = recipients map { user =>
        (user.personalInfo.email, user.email) match {
          case ("", loginEmail)  => loginEmail
          case (contactEmail, _) => contactEmail
        }
      }
      _ <- logger
        .debug(s"Sending emails to ${recipients.length} admins")
        .pure[ConnectionIO]
      _ <- emailAddresses
        .traverse(
          emailAddress =>
            LiftIO[ConnectionIO].liftIO {
              sendEmail(
                publicSettings,
                privateSettings,
                emailAddress,
                emailData.subject,
                emailData.richBody,
                emailData.plainBody
              ).attempt
            }
        )
    } yield { () }
  }
}
