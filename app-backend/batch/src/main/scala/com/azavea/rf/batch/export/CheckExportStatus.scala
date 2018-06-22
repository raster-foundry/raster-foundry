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
import com.azavea.rf.database.{ExportDao, UserGroupRoleDao, ProjectDao, UserDao, PlatformDao}
import com.azavea.rf.database.Implicits._
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

  def isValidEmailSettings(host: String, port: Int, encryption: String, platUserEmail: String, pw: String, userEmail: String): Boolean =
    host.length != 0 &&
      (port == 25 || port == 465 || port == 587 || port == 2525) &&
      encryption.length!= 0 &&
      (encryption == "ssl" || encryption == "tls" || encryption == "starttls") &&
      platUserEmail.length != 0 &&
      pw.length != 0 &&
      userEmail.length != 0

  def exportEmailContent(status: String, user: User, platform: Platform, project: Project) = {
  // TODO: domain needs to be parameterized
    val (subject, content) = status match {
      case status: String if status == "EXPORTED" => ("is ready", "is ready! You")
      case status: String if status == "FAILED" => ("has failed", "has failed. But you")
    }
    (
      s"${platform.name}: Your export ${subject}",
      s"""
      <html>
        <p>${user.name},</p><br>
        <p>Your export in project "${project.name}" ${content} can access
        this project <a href="https://app.rasterfoundry.com/projects/edit/${project.id}/exports">here</a> or any past
        projects you've created at any time <a href="https://app.rasterfoundry.com/projects/">here</a>.</p>
        <p>If you have questions, please feel free to reach out any time at ${platform.publicSettings.emailUser}.</p>
        <p>- The ${platform.name} Team</p>
      </html>
      """,
      s"""
      ${user.name}: Your export in project "${project.name}" ${content} can access this project here:
      https://app.rasterfoundry.com/projects/edit/${project.id}/exports or any past projects you've created
      at any time here: https://app.rasterfoundry.com/projects/ . If you have questions, please feel free to
      reach out any time at ${platform.publicSettings.emailUser}. - The ${platform.name} Team
      """
    )
  }

  def sendExportNotificationEmail(status: String, user: User, platform: Platform, projectO: Option[Project] = None) = {
    val email = new NotificationEmail
    (user.emailNotifications, platform.publicSettings.emailExportNotification) match {
      case (true, true) =>
        val (pub, pri) = (platform.publicSettings, platform.privateSettings)
        (pub.emailSmtpHost, pub.emailSmtpPort, pub.emailSmtpEncryption, pub.emailUser, pri.emailPassword, user.email) match {
          case (host: String, port: Int, encryption: String, platUserEmail: String, pw: String, userEmail: String) if
             isValidEmailSettings(host, port, encryption, platUserEmail, pw, userEmail) =>
             projectO match {
               case Some(project) =>
                 val (subject, html, plain) = exportEmailContent(status, user, platform, project)
                 email.setEmail(host, port, encryption, platUserEmail, pw, userEmail, subject, html, plain).send()
                 logger.info(s"Notified project owner ${user.id} about export ${exportId}.")
               case None => ???
             }
          case _ => logger.warn(email.insufficientSettingsWarning(platform.id.toString(), user.id))
        }
      case (false, true) => logger.warn(email.userEmailNotificationDisabledWarning(user.id))
      case (true, false) => logger.warn(email.platformNotSubscribedWarning(platform.id.toString()))
      case (false, false) => logger.warn(
        email.userEmailNotificationDisabledWarning(user.id) ++ " " ++ email.platformNotSubscribedWarning(platform.id.toString()))
    }
  }

  def notificationInfo(status: String) = {
    val projectIdAndPlatformIO = for {
      export <- ExportDao.query.filter(fr"id = ${exportId}").select
      projectIdO = export.projectId
      ugr <- UserGroupRoleDao.query.filter(fr"user_id = ${export.owner}")
        .filter(fr"group_type = 'PLATFORM'").filter(fr"is_active = true").select
      platform <- PlatformDao.query.filter(ugr.groupId).select
      user <- UserDao.query.filter(fr"id = ${export.owner}").select
    } yield (projectIdO, platform, user)

    val (projectIdO, platform, user) = projectIdAndPlatformIO.transact(xa).unsafeRunSync

    projectIdO match {
      case Some(projectId) =>
        val projectO = ProjectDao.query.filter(projectId).selectOption.transact(xa).unsafeRunSync
        sendExportNotificationEmail(status, user, platform, projectO)
      case None => sendExportNotificationEmail(status, user, platform)
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

    val withLoggingUpdateExportStatus: ConnectionIO[Unit] =
      updateIo(exportId, s3ExportStatus.exportStatus) map {
        case ExportStatus.Failed => {
          logger.info(s"Export finished with ${s3ExportStatus.exportStatus}")
          sendError(s"Export status update failed for ${exportId}")
          notificationInfo("FAILED")
        }
        case ExportStatus.Exported => {
          logger.info(s"Export updated successfully")
          notificationInfo("EXPORTED")
        }
        case _ =>
          logger.info(s"Export ${exportId} has not yet completed: ${s3ExportStatus.exportStatus}")
      }

    withLoggingUpdateExportStatus.transact(xa).unsafeRunSync
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
