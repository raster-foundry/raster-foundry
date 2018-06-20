package com.azavea.rf.common.notification.Email

import com.typesafe.scalalogging.LazyLogging

import org.apache.commons.mail._
import org.apache.commons.mail.Email._
import org.apache.commons.mail.HtmlEmail

class NotificationEmail {

  def insufficientSettingsWarning(platId: String, userId: String): String =
    s"Supplied settings are not sufficient to send an email from Platform: ${platId} to User: ${userId}."

  def userEmailNotificationDisabledWarning(userId: String): String =
    s"User ${userId} disabled email notifications."

  def platformNotSubscribedWarning(platId: String): String =
    s"Platform ${platId} did not subscribe to this notification service."

  def setEmail(host: String, port: Int, uName: String, uPw: String, to: String, subject: String, bodyHtml: String, bodyPlain: String): Email = {
    val email = new HtmlEmail()
    email.setDebug(true)
    email.setHostName(host)
    email.setSSL(true)
    email.setSslSmtpPort(port.toString)
    email.setAuthenticator(new DefaultAuthenticator(uName, uPw))
    email.setFrom(uName)
    email.setSubject(subject)
    email.setHtmlMsg(bodyHtml)
    email.setTextMsg(bodyPlain)
    email.addTo(to)
    email
  }
}
