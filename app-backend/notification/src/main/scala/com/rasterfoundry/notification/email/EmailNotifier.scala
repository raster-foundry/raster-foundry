package com.rasterfoundry.notification.email

import com.rasterfoundry.notification.email.Model._

import org.apache.commons.mail.HtmlEmail
import org.apache.commons.mail._

import java.lang.IllegalArgumentException
import cats.data.Validated

trait EmailNotifier[F[_]] {

  def validateEmailSettings(
      config: EmailConfig,
      fromUserEmail: String,
      toUserEmail: String
  ): Validated[ValidationError, EmailSettings]

}

class NotificationEmail {

  def isValidEmailSettings(
      host: String,
      port: Int,
      encryption: String,
      platUserEmail: String,
      pw: String,
      userEmail: String
  ): Boolean =
    host.length != 0 &&
      (port == 25 || port == 465 || port == 587 || port == 2525) &&
      encryption.length != 0 &&
      (encryption == "ssl" || encryption == "tls" || encryption == "starttls") &&
      platUserEmail.length != 0 &&
      pw.length != 0 &&
      userEmail.length != 0

  def insufficientSettingsWarning(platId: String, userId: String): String =
    s"Supplied settings are not sufficient to send an email from Platform: ${platId} to User: ${userId}."

  def userEmailNotificationDisabledWarning(userId: String): String =
    s"User ${userId} disabled email notifications."

  def platformNotSubscribedWarning(platId: String): String =
    s"Platform ${platId} did not subscribe to this notification service."

  def setEmail(
      conf: EmailConfig,
      to: String,
      subject: String,
      bodyHtml: String,
      bodyPlain: String,
      emailFrom: String,
      emailFromDisplayName: String
  ): Either[Unit, Email] = {

    val email = new HtmlEmail()

    try {
      email.setDebug(true)
      email.setHostName(conf.host)
      if (conf.encryption == "starttls") {
        email.setStartTLSEnabled(true)
        email.setSmtpPort(conf.port);
      } else {
        email.setSSLOnConnect(true)
        email.setSslSmtpPort(conf.port.toString)
      }
      email.setAuthenticator(new DefaultAuthenticator(conf.uName, conf.uPw))
      email.setFrom(emailFrom, emailFromDisplayName)
      email.setSubject(subject)
      email.setHtmlMsg(bodyHtml)
      email.setTextMsg(bodyPlain)
      email.addTo(to)
      Right(email)
    } catch {
      case e: IllegalArgumentException => Left(sendError(e))
      case e: EmailException           => Left(sendError(e))
    }
  }
}
