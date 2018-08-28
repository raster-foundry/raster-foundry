package com.azavea.rf.batch.export

import com.azavea.rf.batch.Job
import com.azavea.rf.common.notification.Email.NotificationEmail
import com.azavea.rf.database._
import com.azavea.rf.database.Implicits._
import com.azavea.rf.database.util.RFTransactor
import com.azavea.rf.datamodel._

import cats.effect.IO
import cats.implicits._
import doobie.{ConnectionIO, Transactor}
import doobie.implicits._
import org.apache.commons.mail.Email

import java.util.UUID

final case class UpdateExportStatus(
    exportId: UUID,
    exportStatus: ExportStatus
)(implicit xa: Transactor[IO])
    extends Job {

  val name = UpdateExportStatus.name

  def run(): Unit = {
    updateExportStatus.transact(xa).unsafeRunSync
    exportStatus match {
      case ExportStatus.Failed =>
        logger.info(s"Export finished with ${exportStatus}")
        sendError(s"Export status update failed for ${exportId}")
        notifyExportOwner("FAILED")
      case ExportStatus.Exported =>
        logger.info(s"Export updated successfully")
        logger.info(s"Updating export owners")
        notifyExportOwner("EXPORTED")
      case _ =>
        logger.info(
          s"Export ${exportId} has not yet completed: ${exportStatus}")
    }
  }

  def notifyExportOwner(status: String): Unit = {
    logger.info(s"Preparing to notify export owners of status: ${status}")
    val export =
      ExportDao.query.filter(exportId).select.transact(xa).unsafeRunSync
    logger.info(s"Retrieved export: ${export.id}")
    val platAndUserIO = for {
      ugr <- UserGroupRoleDao.query
        .filter(fr"user_id = ${export.owner}")
        .filter(fr"group_type = 'PLATFORM'")
        .filter(fr"is_active = true")
        .select
      platform <- PlatformDao.query.filter(ugr.groupId).select
      user <- UserDao.query.filter(fr"id = ${export.owner}").select
    } yield (platform, user)

    logger.info(s"Retrieving Platform and User")
    val (platform, user) = platAndUserIO.transact(xa).unsafeRunSync
    logger.info(s"Retrieved platform (${platform.name}) and user (${user.id})")

    (export.projectId, export.toolRunId) match {
      case (Some(projectId), None) =>
        val project =
          ProjectDao.query.filter(projectId).select.transact(xa).unsafeRunSync
        sendExportNotification(status,
                               user,
                               platform,
                               Some(project.name),
                               project.id,
                               "project")
      case (None, Some(analysisId)) =>
        val analysis =
          ToolRunDao.query.filter(analysisId).select.transact(xa).unsafeRunSync
        sendExportNotification(status,
                               user,
                               platform,
                               analysis.name,
                               analysis.id,
                               "analysis")
      case _ =>
        logger.warn(s"No project or analysis found for export ${exportId}")
    }
  }

  def exportEmailContent(status: String,
                         user: User,
                         platform: Platform,
                         nameO: Option[String],
                         id: UUID,
                         exportType: String): (String, String, String) = {
    val platformHost =
      platform.publicSettings.platformHost.getOrElse("app.rasterfoundry.com")
    val (subject, content): (String, String) = status match {
      case status: String if status == "EXPORTED" =>
        ("is ready", "is ready! You")
      case status: String if status == "FAILED" =>
        ("has failed", "has failed. But you")
    }
    val targetName: String = nameO.getOrElse(id.toString)
    val (targetLink, listLink): (String, String) = exportType match {
      case eType: String if eType == "project" =>
        (s"https://${platformHost}/projects/edit/${id}/exports",
         s"https://${platformHost}/projects/list")
      case eType: String if eType == "analysis" =>
        (s"https://${platformHost}/lab/analysis/${id}",
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

  def sendExportNotification(status: String,
                             user: User,
                             platform: Platform,
                             name: Option[String],
                             id: UUID,
                             exportType: String) = {
    val email = new NotificationEmail

    (user.getEmail, platform.publicSettings.emailExportNotification) match {
      case ("", true) =>
        logger.warn(email.userEmailNotificationDisabledWarning(user.id))
      case ("", false) =>
        logger.warn(
          email.userEmailNotificationDisabledWarning(user.id) ++ " " ++ email
            .platformNotSubscribedWarning(platform.id.toString()))
      case (emailAddress, true) =>
        val (pub, pri) = (platform.publicSettings, platform.privateSettings)
        (pub.emailSmtpHost,
         pub.emailSmtpPort,
         pub.emailSmtpEncryption,
         pub.emailUser,
         pri.emailPassword,
         emailAddress) match {
          case (host: String,
                port: Int,
                encryption: String,
                platUserEmail: String,
                pw: String,
                userEmail: String)
              if email.isValidEmailSettings(host,
                                            port,
                                            encryption,
                                            platUserEmail,
                                            pw,
                                            userEmail) =>
            val (subject, html, plain) =
              exportEmailContent(status, user, platform, name, id, exportType)
            email
              .setEmail(host,
                        port,
                        encryption,
                        platUserEmail,
                        pw,
                        userEmail,
                        subject,
                        html,
                        plain)
              .map((configuredEmail: Email) => configuredEmail.send)
            logger.info(s"Notified owner ${user.id} about export ${exportId}.")
          case _ =>
            logger.warn(
              email.insufficientSettingsWarning(platform.id.toString(),
                                                user.id))
        }
      case (_, false) =>
        logger.warn(email.platformNotSubscribedWarning(platform.id.toString()))
    }
  }

  def updateExportStatus: ConnectionIO[Unit] =
    for {
      _ <- logger.info(s"Getting user with id: $systemUser").pure[ConnectionIO]
      user <- UserDao.unsafeGetUserById(systemUser)
      _ <- logger.info(s"Getting export with id: $exportId").pure[ConnectionIO]
      export <- ExportDao.unsafeGetExportById(exportId)
      copied = export.copy(exportStatus = this.exportStatus)
      _ <- logger
        .info(s"Setting export status to ${copied.exportStatus}")
        .pure[ConnectionIO]
      update <- ExportDao.update(copied, exportId, user)
    } yield {
      logger.info("Successfully updated export status")
    }

}

object UpdateExportStatus {
  val name = "update_export_status"
  implicit val xa = RFTransactor.xa

  def main(args: Array[String]): Unit = {
    val job = args.toList match {
      case List(exportId, exportStatus) =>
        UpdateExportStatus(
          UUID.fromString(exportId),
          ExportStatus.fromString(exportStatus)
        )
      case _ =>
        throw new IllegalArgumentException(
          s"Arguments could not be parsed to UUID and export status: ${args}"
        )
    }

    job.run()
  }
}
