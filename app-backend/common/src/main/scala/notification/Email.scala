package com.azavea.rf.common.notification.Email

import com.azavea.rf.common.RollbarNotifier

import org.apache.commons.mail._
import org.apache.commons.mail.Email._
import org.apache.commons.mail.HtmlEmail

import java.lang.IllegalArgumentException

class NotificationEmail extends RollbarNotifier {

  def isValidEmailSettings(host: String,
                           port: Int,
                           encryption: String,
                           platUserEmail: String,
                           pw: String,
                           userEmail: String): Boolean =
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

  def setEmail(host: String,
               port: Int,
               encryption: String,
               uName: String,
               uPw: String,
               to: String,
               subject: String,
               bodyHtml: String,
               bodyPlain: String): Either[Unit, Email] = {

    val email = new HtmlEmail()

    try {
      email.setDebug(true)
      email.setHostName(host)
      if (encryption == "starttls") {
        email.setStartTLSEnabled(true)
        email.setSmtpPort(port);
      } else {
        email.setSSLOnConnect(true)
        email.setSslSmtpPort(port.toString)
      }
      email.setAuthenticator(new DefaultAuthenticator(uName, uPw))
      email.setFrom(uName)
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
