package com.rasterfoundry.batch.export

import com.rasterfoundry.batch.Job
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database._
import com.rasterfoundry.database.notification.Notify
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.datamodel._
import com.rasterfoundry.notification.email.Model._

import cats.effect.{IO, LiftIO}
import cats.implicits._
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}

import java.util.UUID

final case class UpdateExportStatus(
    exportId: UUID,
    exportStatus: ExportStatus
)(implicit xa: Transactor[IO])
    extends Job {

  val name = UpdateExportStatus.name

  def runJob(args: List[String]): IO[Unit] = {
    logger.info(s"Running update export status job...")
    for {
      _ <- updateExportStatus.transact(xa)
      _ <- exportStatus match {
        case ExportStatus.Failed =>
          logger.info(s"Export finished with ${exportStatus}")
          sendError(s"Export status update failed for ${exportId}")
          notifyExportOwner("FAILED")
        case ExportStatus.Exported =>
          logger.info(s"Export updated successfully")
          logger.info(s"Updating export owners")
          notifyExportOwner("EXPORTED")
        case _ =>
          IO {
            logger.info(
              s"Export ${exportId} has not yet completed: ${exportStatus}"
            )
          }
      }
    } yield ()
  }

  def notifyExportOwner(status: String): IO[Unit] = {
    logger.info(s"Preparing to notify export owners of status: ${status}")
    (for {
      export <- ExportDao.query.filter(exportId).select
      ugr <- UserGroupRoleDao.query
        .filter(fr"user_id = ${export.owner}")
        .filter(fr"group_type = 'PLATFORM'")
        .filter(fr"is_active = true")
        .select
      platform <- PlatformDao.query.filter(ugr.groupId).select
      user <- UserDao.query.filter(fr"id = ${export.owner}").select
      _ <- (export.projectId, export.toolRunId) match {
        case (Some(projectId), None) =>
          ProjectDao.query.filter(projectId).select flatMap { project =>
            LiftIO[ConnectionIO].liftIO(
              sendExportNotification(
                status,
                user,
                platform,
                Some(project.name),
                project.id,
                "project"
              )
            )
          }
        case (None, Some(analysisId)) =>
          ToolRunDao.query
            .filter(analysisId)
            .select map { analysis =>
            LiftIO[ConnectionIO].liftIO(
              sendExportNotification(
                status,
                user,
                platform,
                analysis.name,
                analysis.id,
                "analysis"
              )
            )
          }
        case _ =>
          logger.warn(s"No project or analysis found for export ${exportId}").pure[ConnectionIO]
      }
    } yield ()).transact(xa)
  }

  def exportEmailContent(
      status: String,
      user: User,
      platform: Platform,
      nameO: Option[String],
      id: UUID,
      exportType: String
  ): (Subject, HtmlBody, PlainBody) = {
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
        (
          s"https://${platformHost}/projects/edit/${id}/exports",
          s"https://${platformHost}/projects/list"
        )
      case eType: String if eType == "analysis" =>
        (
          s"https://${platformHost}/lab/analysis/${id}",
          s"https://${platformHost}/lab/browse/analyses"
        )
    }

    (
      Subject(s"${platform.name}: Your export ${subject}"),
      HtmlBody(s"""
      <html>
        <p>${user.name},</p><br>
        <p>Your export in ${exportType} "${targetName}" ${content} can access
        this ${exportType} <a href="${targetLink}" target="_blank">here</a> or any past
        projects you've created at any time <a href="${listLink}" target="_blank">here</a>.</p>
        <p>If you have questions, please feel free to reach out any time at ${platform.publicSettings.emailSupport}.</p>
        <p>- The ${platform.name} Team</p>
      </html>
      """),
      PlainBody(
        s"""
      ${user.name}:
      Your export in ${exportType} "${targetName}" ${content} can access this ${exportType} here: ${targetLink},
      or any past projects you've created at any time here: ${listLink}.
      If you have questions, please feel free to reach out any time at ${platform.publicSettings.emailSupport}.
      - The ${platform.name} Team
      """
      )
    )
  }

  def sendExportNotification(
      status: String,
      user: User,
      platform: Platform,
      name: Option[String],
      id: UUID,
      exportType: String
  ): IO[Unit] = {
    (user.getEmail, platform.publicSettings.emailExportNotification) match {
      case (emailAddress, true) =>
        val (subject, html, plain) =
          exportEmailContent(status, user, platform, name, id, exportType)
        Notify.sendEmail(
          platform.publicSettings,
          platform.privateSettings,
          emailAddress,
          subject.underlying,
          html.underlying,
          plain.underlying
        )
      case (_, false) =>
        IO {
          logger.warn(
            s"Platform ${platform.id} did not subscribe to this notification service."
          )
        }
    }
  }

  def updateExportStatus: ConnectionIO[Unit] =
    for {
      _ <- logger.info(s"Getting export with id: $exportId").pure[ConnectionIO]
      export <- ExportDao.unsafeGetExportById(exportId)
      copied = export.copy(exportStatus = this.exportStatus)
      _ <- logger
        .info(s"Setting export status to ${copied.exportStatus}")
        .pure[ConnectionIO]
      _ <- ExportDao.update(copied, exportId)
    } yield {
      logger.info("Successfully updated export status")
    }

}

object UpdateExportStatus extends Job {
  val name = "update_export_status"

  def runJob(args: List[String]): IO[Unit] = {
    RFTransactor.xaResource
      .use(xa => {
        implicit val transactor = xa
        val job = args match {
          case List(exportId, exportStatus) =>
            logger.info(
              s"Updating export ${exportId} of status ${exportStatus}..."
            )
            UpdateExportStatus(
              UUID.fromString(exportId),
              ExportStatus.fromString(exportStatus)
            )
          case _ =>
            throw new IllegalArgumentException(
              s"Arguments could not be parsed to UUID and export status: ${args}"
            )
        }

        job.runJob(args) handleErrorWith {
          case e: Throwable =>
            IO {
              sendError(e)
            }
        }
      })
  }
}
