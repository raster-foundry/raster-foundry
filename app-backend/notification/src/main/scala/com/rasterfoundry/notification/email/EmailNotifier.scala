package com.rasterfoundry.notification.email

import com.rasterfoundry.notification.email.Model._

import cats.effect.Sync
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.apache.commons.mail.HtmlEmail
import org.apache.commons.mail._

trait EmailNotifier[F[_]] {

  def validateEmailSettings(
      config: EmailConfig,
      fromUserEmail: FromEmailAddress,
      toUserEmail: ToEmailAddress
  ): Either[String, EmailSettings]

  def buildEmail(
      settings: EmailSettings,
      displayName: FromEmailDisplayName,
      subject: Subject,
      bodyHtml: HtmlBody,
      bodyPlain: PlainBody
  ): F[Email]

  def sendEmail(email: Email): F[Unit]
}

class LiveEmailNotifier[F[_]: Sync] extends EmailNotifier[F] {

  implicit val unsafeLoggerF = Slf4jLogger.getLogger[F]

  def validateEmailSettings(
      config: EmailConfig,
      fromUserEmail: FromEmailAddress,
      toUserEmail: ToEmailAddress
  ): Either[String, EmailSettings] =
    if (!config.host.underlying.isEmpty &&
        Set(25, 465, 587, 2525).contains(config.port.underlying) &&
        !config.username.underlying.isEmpty &&
        !config.password.underlying.isEmpty &&
        !fromUserEmail.underlying.isEmpty &&
        !toUserEmail.underlying.isEmpty) {
      Right(EmailSettings(config, fromUserEmail, toUserEmail))
    } else {
      Left(
        "Email settings were insufficient to send email. Check configuration."
      )
    }

  def buildEmail(
      settings: EmailSettings,
      displayName: FromEmailDisplayName,
      subject: Subject,
      bodyHtml: HtmlBody,
      bodyPlain: PlainBody
  ): F[Email] = {
    val email = new HtmlEmail()

    Sync[F]
      .delay({
        email.setDebug(true)
        email.setHostName(settings.config.host.underlying)
        if (settings.config.encryption == StartTLS) {
          email.setStartTLSEnabled(true)
          email.setSmtpPort(settings.config.port.underlying)
        } else {
          email.setSSLOnConnect(true)
          email.setSslSmtpPort(settings.config.port.underlying.toString)
        }
        email.setAuthenticator(
          new DefaultAuthenticator(
            settings.config.username.underlying,
            settings.config.password.underlying
          )
        )
        email.setFrom(settings.fromUserEmail.underlying, displayName.underlying)
        email.setSubject(subject.underlying)
        email.setHtmlMsg(bodyHtml.underlying)
        email.setTextMsg(bodyPlain.underlying)
        email.addTo(settings.toUserEmail.underlying)
      })
  }

  def sendEmail(email: Email): F[Unit] =
    Sync[F].delay {
      email.send
    } flatMap { s =>
      Logger[F].debug(s)
    }
}
