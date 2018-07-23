package com.azavea.rf.batch.export

import java.net.URI
import java.util.UUID

import cats.data._
import cats.effect.IO
import cats.implicits._
import com.azavea.rf.batch._
import com.azavea.rf.batch.export.json.S3ExportStatus
import com.azavea.rf.batch.util._
import com.azavea.rf.datamodel._
import com.azavea.rf.common.notification.Email.NotificationEmail
import io.circe.parser.decode
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import Fragments._
import doobie.util.transactor.Transactor
import com.azavea.rf.database.{ExportDao, UserGroupRoleDao, ProjectDao, UserDao, PlatformDao, ToolRunDao}
import com.azavea.rf.database.Implicits._
import org.apache.commons.mail.Email
import com.azavea.rf.database.util.RFTransactor

import scala.concurrent.duration._
import scala.io.Source
import scala.util._

case class CheckExportStatus(exportId: UUID, statusURI: URI, time: Duration = 60.minutes, region: Option[String] = None)(implicit val xa: Transactor[IO]) extends Job {
  val name = CreateExportDef.name

  /** Get S3 client per each call */
  def s3Client = S3(region = region)

  def updateExportStatus(export: Export, status: ExportStatus): Export =
    export.copy(exportStatus = status)

  def exportEmailContent(status: String, user: User, platform: Platform, nameO: Option[String], id: UUID, exportType: String): (String, String, String) = {
    val platformHost = platform.publicSettings.platformHost.getOrElse("app.rasterfoundry.com")
    val (subject, content): (String, String) = status match {
      case status: String if status == "EXPORTED" => ("is ready", "is ready! You")
      case status: String if status == "FAILED" => ("has failed", "has failed. But you")
    }
    val targetName: String = nameO.getOrElse(id.toString)
    val (targetLink, listLink): (String, String) = exportType match {
      case eType: String if eType == "project" => (
        s"https://${platformHost}/projects/edit/${id}/exports",
        s"https://${platformHost}/projects/list")
      case eType: String if eType == "analysis" => (
        s"https://${platformHost}/lab/analysis/${id}",
        s"https://${platformHost}/lab/browse/analyses")
    }

    (
      s"${platform.name}: Your export ${subject}",
      s"""
      <html>
        <p>${user.name},</p><br>
        <p>Your export in ${exportType} "${targetName}" ${content} can access
        this ${exportType} <a href="${targetLink}" target="_blank">here</a> or any past
        projects you've created at any time <a href="${listLink}" target="_blank">here</a>.</p>
        <p>If you have questions, please feel free to reach out any time at ${platform.publicSettings.emailUser}.</p>
        <p>- The ${platform.name} Team</p>
      </html>
      """,
      s"""
      ${user.name}:
      Your export in ${exportType} "${targetName}" ${content} can access this ${exportType} here: ${targetLink},
      or any past projects you've created at any time here: ${listLink}.
      If you have questions, please feel free to reach out any time at ${platform.publicSettings.emailUser}.
      - The ${platform.name} Team
      """
    )
  }

  def sendExportNotification(status: String, user: User, platform: Platform, name: Option[String], id: UUID, exportType: String) = {
    val email = new NotificationEmail

    val userEmailAddress: String = (user.emailNotifications, user.personalInfo.emailNotifications) match {
      case (true, true) | (false, true) => user.personalInfo.email
      case (true, false) => user.email
      case (false, false) => ""
    }

    (userEmailAddress, platform.publicSettings.emailExportNotification) match {
      case ("", true) => logger.warn(email.userEmailNotificationDisabledWarning(user.id))
      case ("", false) => logger.warn(
        email.userEmailNotificationDisabledWarning(user.id) ++ " " ++ email.platformNotSubscribedWarning(platform.id.toString()))
      case (userEmailAddress, true) =>
        val (pub, pri) = (platform.publicSettings, platform.privateSettings)
        (pub.emailSmtpHost, pub.emailSmtpPort, pub.emailSmtpEncryption, pub.emailUser, pri.emailPassword, userEmailAddress) match {
          case (host: String, port: Int, encryption: String, platUserEmail: String, pw: String, userEmail: String) if
             email.isValidEmailSettings(host, port, encryption, platUserEmail, pw, userEmail) =>
             val (subject, html, plain) = exportEmailContent(status, user, platform, name, id, exportType)
             email.setEmail(host, port, encryption, platUserEmail, pw, userEmail, subject, html, plain).map((configuredEmail: Email) => configuredEmail.send)
             logger.info(s"Notified owner ${user.id} about export ${exportId}.")
          case _ => logger.warn(email.insufficientSettingsWarning(platform.id.toString(), user.id))
        }
      case (_, false) => logger.warn(email.platformNotSubscribedWarning(platform.id.toString()))
    }

    // (user.emailNotifications, platform.publicSettings.emailExportNotification) match {
    //   case (true, true) =>
    //     val (pub, pri) = (platform.publicSettings, platform.privateSettings)
    //     (pub.emailSmtpHost, pub.emailSmtpPort, pub.emailSmtpEncryption, pub.emailUser, pri.emailPassword, user.email) match {
    //       case (host: String, port: Int, encryption: String, platUserEmail: String, pw: String, userEmail: String) if
    //          email.isValidEmailSettings(host, port, encryption, platUserEmail, pw, userEmail) =>
    //          val (subject, html, plain) = exportEmailContent(status, user, platform, name, id, exportType)
    //          email.setEmail(host, port, encryption, platUserEmail, pw, userEmail, subject, html, plain).map((configuredEmail: Email) => configuredEmail.send)
    //          logger.info(s"Notified owner ${user.id} about export ${exportId}.")
    //       case _ => logger.warn(email.insufficientSettingsWarning(platform.id.toString(), user.id))
    //     }
    //   case (false, true) => logger.warn(email.userEmailNotificationDisabledWarning(user.id))
    //   case (true, false) => logger.warn(email.platformNotSubscribedWarning(platform.id.toString()))
    //   case (false, false) => logger.warn(
    //     email.userEmailNotificationDisabledWarning(user.id) ++ " " ++ email.platformNotSubscribedWarning(platform.id.toString()))
    // }
  }

  def notifyExportOwner(status: String): Unit = {
    logger.info(s"Preparing to notify export owners of status: ${status}")
    val export = ExportDao.query.filter(fr"id = ${exportId}").select.transact(xa).unsafeRunSync
    logger.info(s"Retrieved export: ${export}")
    val platAndUserIO = for {
      ugr <- UserGroupRoleDao.query.filter(fr"user_id = ${export.owner}")
        .filter(fr"group_type = 'PLATFORM'").filter(fr"is_active = true").select
      platform <- PlatformDao.query.filter(ugr.groupId).select
      user <- UserDao.query.filter(fr"id = ${export.owner}").select
    } yield (platform, user)

    logger.info(s"Retrieving Platform and User")
    val (platform, user) = platAndUserIO.transact(xa).unsafeRunSync
    logger.info(s"Retrieved platform (${platform})and user (${user})")

    (export.projectId, export.toolRunId) match {
      case (Some(projectId), None) =>
        val project = ProjectDao.query.filter(projectId).select.transact(xa).unsafeRunSync
        sendExportNotification(status, user, platform, Some(project.name), project.id, "project")
      case (None, Some(analysisId)) =>
        val analysis = ToolRunDao.query.filter(analysisId).select.transact(xa).unsafeRunSync
        sendExportNotification(status, user, platform, analysis.name, analysis.id, "analysis")
      case _ => logger.warn(s"No project or analysis found for export ${exportId}")
    }
  }

  def run: Unit = {
    logger.info(s"Checking export ${exportId} process...")
    val json =
      try {
        retry(time, 30.seconds) {
          Source
            .fromInputStream(s3Client.getObject(statusURI).getObjectContent)
            .getLines
            .mkString(" ")
        }
      } catch {
        case e: Throwable =>
          logger.error(e.stackTraceString)
          sendError(e.stackTraceString)
          stop
          sys.exit(1)
      }

    val s3ExportStatus =
      decode[S3ExportStatus](json) match {
        case Right(r) => r
        case _ => {
          logger.error("Incorrect S3ExportStatus JSON")
          sys.exit(1)
        }
      }

    def updateIo(exportId: UUID, exportStatus: ExportStatus): ConnectionIO[ExportStatus] =
      sql"update exports set export_status = ${exportStatus} where id = ${exportId}"
        .update.withUniqueGeneratedKeys("export_status")

    updateIo(exportId, s3ExportStatus.exportStatus).transact(xa).unsafeRunSync()
    s3ExportStatus.exportStatus match {
      case ExportStatus.Failed => {
        logger.info(s"Export finished with ${s3ExportStatus.exportStatus}")
        sendError(s"Export status update failed for ${exportId}")
        notifyExportOwner("FAILED")
      }
      case ExportStatus.Exported => {
        logger.info(s"Export updated successfully")
        logger.info(s"Updating export owners")
        notifyExportOwner("EXPORTED")
      }
      case _ =>
        logger.info(s"Export ${exportId} has not yet completed: ${s3ExportStatus.exportStatus}")
    }
  }
}

object CheckExportStatus {
  val name = "check_export_status"
  implicit val xa = RFTransactor.xa

  def main(args: Array[String]): Unit = {

    val job = args.toList match {
      case List(exportId, statusURI, duration, region) => CheckExportStatus(UUID.fromString(exportId), new URI(statusURI), Duration(duration), Some(region))
      case List(exportId, statusURI, duration) => CheckExportStatus(UUID.fromString(exportId), new URI(statusURI), Duration(duration))
      case List(exportId, statusURI) => CheckExportStatus(UUID.fromString(exportId), new URI(statusURI))
      case _ =>
        throw new IllegalArgumentException("Argument could not be parsed to UUID")
    }

    job.run
  }
}
