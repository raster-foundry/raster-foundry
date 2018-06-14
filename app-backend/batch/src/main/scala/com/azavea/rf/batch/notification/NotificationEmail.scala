package com.azavea.rf.batch.notification

import com.typesafe.scalalogging.LazyLogging
import java.util.UUID

import cats.effect.IO
import org.apache.commons.mail._
import org.apache.commons.mail.Email._

import scala.concurrent.Future
import com.azavea.rf.batch.Job
import com.azavea.rf.datamodel._
import com.azavea.rf.database.PlatformDao
import doobie.util.transactor.Transactor
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import com.azavea.rf.database.util.RFTransactor

case class NotificationEmail(userIds: List[String], subject: String, content: String, subscribeType: String)(implicit val xa: Transactor[IO]) extends LazyLogging {

  val platformsWithUsersIO = for {
    platformsWithUsers <- PlatformDao.getPlatformsAndUsersByUsersId(userIds)
  } yield platformsWithUsers

  val platformsWithUsers = platformsWithUsersIO.transact(xa).unsafeRunSync()

  def insufficientSettingsWarning(platId: String, userId: String): String =
    s"Supplied settings are not sufficient to send an email from Platform: ${platId} to User: ${userId}."

  def userEmailNotificationDisabledWarning(userId: String): String =
    s"User ${userId} disabled email notifications."

  def platformNotSubscribedWarning(platId: String): String =
    s"Platform ${platId} did not subscribe to ${subscribeType} notifications."

  val noMatchPlatAndUserWarning: String = "No matched platform and user record."

  private def setEmail(host: String, port: Int, uName: String, uPw: String, subject: String, content: String, to: String): Email = {
    val email = new SimpleEmail()
    email.setDebug(true)
    email.setHostName(host)
    email.setSSL(true)
    email.setSslSmtpPort(port.toString)
    email.setAuthenticator(new DefaultAuthenticator(uName, uPw))
    email.setFrom(uName)
    email.setSubject(subject)
    email.setMsg(content)
    email.addTo(to)
    email
  }

  def sendIngestStatusEmail() =  {
    platformsWithUsers.map(pU => {
      (pU.emailNotifications, pU.pubSettings.emailIngestNotification) match {
        case (true, true) =>
          (pU.pubSettings.emailSmtpHost, pU.pubSettings.emailUser, pU.priSettings.emailPassword, pU.email) match {
            case (host: String, platUserEmail: String, pw: String, userEmail: String) if
              host.length != 0 && platUserEmail.length != 0 && pw.length != 0 && userEmail.length != 0 =>
              setEmail(host, 465, platUserEmail, pw, subject, content, userEmail).send()
            case (_, _, _, _) => logger.warn(
              insufficientSettingsWarning(pU.platId.toString(), pU.uId))
          }
        case (false, true) => logger.warn(
          userEmailNotificationDisabledWarning(pU.uId))
        case (true, false) => logger.warn(
          platformNotSubscribedWarning(pU.platId.toString()))
        case (false, false) => logger.warn(
          userEmailNotificationDisabledWarning(pU.uId) ++ " " ++ platformNotSubscribedWarning(pU.platId.toString()))
      }
    })
  }

  def sendEmail() = {
    if (subscribeType == "ingest") {
      sendIngestStatusEmail()
    }
    // TODO: take care of other subscribe types
  }
}
