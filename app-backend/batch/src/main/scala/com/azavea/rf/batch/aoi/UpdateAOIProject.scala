package com.azavea.rf.batch.aoi

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

import cats.effect.IO
import cats.implicits._
import com.azavea.rf.batch.Job
import com.azavea.rf.common.AWSBatch
import com.azavea.rf.common.notification.Email.NotificationEmail
import com.azavea.rf.database.Implicits._
import com.azavea.rf.database._
import com.azavea.rf.database.util.RFTransactor
import com.azavea.rf.datamodel._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import io.circe.Decoder.Result
import io.circe._
import geotrellis.vector._
import org.apache.commons.mail.Email

final case class UpdateAOIProject(projectId: UUID)(
    implicit val xa: Transactor[IO])
    extends Job
    with AWSBatch {
  val name = FindAOIProjects.name

  type LastChecked = Timestamp
  type StartTime = Timestamp

  def aoiEmailContent(project: Project,
                      platform: Platform,
                      user: User,
                      sceneCount: Int): (String, String, String) = {
    val platformHost =
      platform.publicSettings.platformHost.getOrElse("app.rasterfoundry.com")
    (
      s"""
        ${platform.name}: New Scenes Updated to Your AOI Project "${project.name}"
      """,
      s"""
      <html>
        <p>${user.name},</p><br>
        <p>You have ${sceneCount} new scenes updated to your AOI project "${project.name}"! You can access
        these new scenes <a href="https://${platformHost}/projects/edit/${project.id}/scenes" target="_blank">here</a> or any past
        projects you've created at any time <a href="https://${platformHost}/projects/list" target="_blank">here</a>.</p>
        <p>If you have questions, please feel free to reach out any time at ${platform.publicSettings.emailUser}.</p>
        <p>- The ${platform.name} Team</p>
      </html>
      """,
      s"""
      ${user.name}: You have ${sceneCount} new scenes updated to your AOI project "${project.name}"! You can access
      these new scenes here: https://${platformHost}/projects/edit/${project.id}/scenes , or any past
      projects you've created at any time here: https://${platformHost}/projects/list . If you have questions,
      please feel free to reach out any time at ${platform.publicSettings.emailUser}. - The ${platform.name} Team
      """
    )
  }

  def sendAoiNotificationEmail(project: Project,
                               platform: Platform,
                               user: User,
                               sceneCount: Int) = {
    val email = new NotificationEmail

    (user.getEmail, platform.publicSettings.emailAoiNotification) match {
      case ("", true) =>
        logger.warn(email.userEmailNotificationDisabledWarning(user.id))
      case ("", false) =>
        logger.warn(
          email.userEmailNotificationDisabledWarning(user.id) ++ " " ++ email
            .platformNotSubscribedWarning(platform.id.toString()))
      case (userEmailAddress, true) =>
        // send emails
        val (pub, pri) = (platform.publicSettings, platform.privateSettings)
        (pub.emailSmtpHost,
         pub.emailSmtpPort,
         pub.emailSmtpEncryption,
         pub.emailUser,
         pri.emailPassword,
         userEmailAddress) match {
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
              aoiEmailContent(project, platform, user, sceneCount)
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
            logger.info(s"Notified project owner ${user.id} about AOI updates")
          case _ =>
            logger.warn(
              email.insufficientSettingsWarning(platform.id.toString(),
                                                user.id))
        }
      case (_, false) =>
        logger.warn(email.platformNotSubscribedWarning(platform.id.toString()))
    }
  }

  def notifyProjectOwner(projId: UUID, sceneCount: Int) = {
    if (sceneCount > 0) {
      val project =
        ProjectDao.query.filter(projId).select.transact(xa).unsafeRunSync

      if (project.owner == auth0Config.systemUser) {
        logger.warn(
          s"Owner of project ${projId} is a system user. Email is not sent.")
      } else {
        val platAndUserIO = for {
          ugr <- UserGroupRoleDao.query
            .filter(fr"user_id = ${project.owner}")
            .filter(fr"group_type = 'PLATFORM'")
            .filter(fr"is_active = true")
            .select
          platform <- PlatformDao.query.filter(ugr.groupId).select
          user <- UserDao.query.filter(fr"id = ${project.owner}").select
        } yield (platform, user)
        val (platform, user) = platAndUserIO.transact(xa).unsafeRunSync
        sendAoiNotificationEmail(project, platform, user, sceneCount)
      }
    } else {
      logger.warn(
        s"AOI project ${projId.toString} has no new scenes available. Project owner will not be notified.")
    }
  }

  def run(): Unit = {
    logger.info(s"Updating project ${projectId}")

    /** Fetch the project, AOI area, last checked time, start time, and AOI scene query parameters */
    def fetchBaseData: ConnectionIO[(String,
                                     Projected[MultiPolygon],
                                     StartTime,
                                     LastChecked,
                                     Result[CombinedSceneQueryParams])] = {
      val base = fr"""
      SELECT
        proj_owner, geometry, start_time, aois_last_checked, filters
      FROM
        (
          (select id proj_table_id, owner proj_owner, aois_last_checked from projects) projects_filt
          join aois on projects_filt.proj_table_id = aois.project_id
          join shapes on aois.shape = shapes.id
        )
      """
      val projectIdFilter: Option[Fragment] = Some(
        fr"proj_table_id = ${projectId}")

      // This could return a list probably if we ever support > 1 AOI per project
      (base ++ Fragments.whereAndOpt(projectIdFilter))
        .query[(String, Projected[MultiPolygon], StartTime, LastChecked, Json)]
        .unique
        .map(
          {
            case (projOwner: String,
                  g: Projected[MultiPolygon],
                  startTime: StartTime,
                  lastChecked: LastChecked,
                  f: Json) =>
              (projOwner,
               g,
               startTime,
               lastChecked,
               f.as[CombinedSceneQueryParams])
          }
        )
    }

    /** Find all the scenes that can be added to a project */
    def fetchProjectScenes(user: User,
                           geom: Projected[MultiPolygon],
                           startTime: StartTime,
                           lastChecked: LastChecked,
                           queryParams: Option[CombinedSceneQueryParams])
      : ConnectionIO[List[UUID]] = {
      val baseParams = queryParams.getOrElse(CombinedSceneQueryParams())
      val augmentedQueryParams =
        baseParams.copy(
          sceneParams = baseParams.sceneParams.copy(
            minAcquisitionDatetime = Some(startTime)
          ),
          timestampParams = baseParams.timestampParams.copy(
            minCreateDatetime = Some(lastChecked)
          )
        )
      SceneDao
        .authQuery(user, ObjectType.Scene)
        .filter(geom)
        .filter(augmentedQueryParams)
        .list
        .map { (scenes: List[Scene]) =>
          scenes map { _.id }
        }
    }

    def addScenesToProjectWithProjectIO: ConnectionIO[(UUID, List[UUID])] = {
      for {
        baseData <- fetchBaseData
        (userId, g, startTime, lastChecked, qp) = baseData
        user <- UserDao.unsafeGetUserById(userId)
        sceneIds <- fetchProjectScenes(user,
                                       g,
                                       startTime,
                                       lastChecked,
                                       qp.toOption)
        _ <- logger.info(s"Found ${sceneIds.length} scenes").pure[ConnectionIO]
        _ <- updateProjectIO(user, projectId)
        _ <- ProjectDao.addScenesToProject(sceneIds, projectId, false)
      } yield { (projectId, sceneIds) }
    }

    def updateProjectIO(user: User, projectId: UUID): ConnectionIO[Int] =
      for {
        proj <- ProjectDao.unsafeGetProjectById(projectId)
        newProject = proj.copy(aoisLastChecked = Timestamp.from(Instant.now))
        affectedRows <- ProjectDao.updateProject(newProject, proj.id, user)
      } yield affectedRows

    val (projId, numberNewScenes) =
      addScenesToProjectWithProjectIO.transact(xa).unsafeRunSync

    notifyProjectOwner(projId, numberNewScenes.length)
  }
}

object UpdateAOIProject {
  val name = "update_aoi_project"

  implicit val xa = RFTransactor.xa

  def main(args: Array[String]): Unit = {

    val job = args.toList match {
      case List(projectId) => UpdateAOIProject(UUID.fromString(projectId))
      case _ =>
        throw new IllegalArgumentException(
          "Argument could not be parsed to UUID")
    }

    job.run
  }
}
