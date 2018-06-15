package com.azavea.rf.batch.notification

import com.typesafe.scalalogging.LazyLogging
import java.util.UUID

import cats.effect.IO

import scala.concurrent.Future
import com.azavea.rf.batch.Job
import com.azavea.rf.datamodel._
import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.common.{RollbarNotifier, S3}
import com.azavea.rf.database.{ProjectDao, SceneDao, PlatformDao}
import doobie.util.transactor.Transactor
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import Fragments._
import com.azavea.rf.database.util.RFTransactor

case class NotifyIngestStatus(sceneId: UUID)(implicit val xa: Transactor[IO]) extends Job
  with RollbarNotifier {

  val name = NotifyIngestStatus.name

  def getSceneConsumers(sceneId: UUID): ConnectionIO[List[String]] = {
    for {
      projects <- ProjectDao.query.filter(fr"id IN (SELECT project_id FROM scenes_to_projects WHERE scene_id = ${sceneId})").list
    } yield projects.map(_.owner)
  }

  def getSceneOwner(sceneId: UUID): ConnectionIO[Option[String]] = {
    val ownerIO = for {
      scene <- SceneDao.query.filter(sceneId).select
    } yield scene.owner

    ownerIO.map(owner => {
      owner == auth0Config.systemUser match {
        case true => None
        case false => Some(owner)
      }
    })
  }

  def createIngestEmailContentForConsumers(pU: PlatformWithUsersSceneProjects, scene: Scene, ingestStatus: String): (String, String, String) = {
    // TODO: domain needs to be parameterized
    ingestStatus match {
      case status: String if status == "INGESTED" =>
        (
          s"${pU.platName}: Your ${pU.projectName}'s scene ${scene.name} is ready to view",
          s"""
          <html>
            <p>${pU.uName},</p><br>
            <p>The scene "${scene.name}" has been successfully ingested into your project: ${pU.projectName}! You can access
            this project <a href="https://app.rasterfoundry.com/projects/edit/${pU.projectId}/scenes">here</a> or any past
            projects you've created at any time <a href="https://app.rasterfoundry.com/projects/">here</a>.</p>
            <p>If you have questions, please feel free to reach out any time at ${pU.pubSettings.emailUser}.</p>
            <p>- The ${pU.platName} Team</p>
          </html>
          """,
          s"""
          ${pU.uName}: The scene "${scene.name}" has been successfully ingested into your project: ${pU.projectName}!
          You can access this project at here: https://app.rasterfoundry.com/projects/edit/${pU.projectId}/scenes or
          any past projects you've created at any time here: https://app.rasterfoundry.com/projects/ . If you have
          questions, please feel free to reach out any time at ${pU.pubSettings.emailUser}. - The ${pU.platName} Team
          """
        )
      case status: String if status == "FAILED" =>
        (
          s"${pU.platName}: Your ${pU.projectName}'s scene ${scene.name} failed to ingest",
          s"""
          <html>
            <p>${pU.uName},</p><br>
            <p>The scene "${scene.name}" in your project: ${pU.projectName} has failed to ingest. But you can access
            this project <a href="https://app.rasterfoundry.com/projects/edit/${pU.projectId}/scenes">here</a> or any past
            projects you've created at any time <a href="https://app.rasterfoundry.com/projects/">here</a>.</p>
            <p>If you have questions, please feel free to reach out any time at ${pU.pubSettings.emailUser}.</p>
            <p>- The ${pU.platName} Team</p>
          </html>
          """,
          s"""
          ${pU.uName}: The scene "${scene.name}" in your project: ${pU.projectName} has failed to ingest. But you can
          access this project at here: https://app.rasterfoundry.com/projects/edit/${pU.projectId}/scenes or
          any past projects you've created at any time here: https://app.rasterfoundry.com/projects/ . If you have
          questions, please feel free to reach out any time at ${pU.pubSettings.emailUser}. - The ${pU.platName} Team
          """
        )
    }
  }

  def createIngestEmailContentForOwner(pO: PlatformWithSceneOwner, scene: Scene, ingestStatus: String): (String, String, String) = {
    ingestStatus match {
      case status: String if status == "INGESTED" =>
        (
          s"${pO.platName}: Your scene ${scene.name} is ready to view",
          s"""
          <html>
            <p>${pO.uName},</p><br>
            <p>The scene "${scene.name}" has been successfully ingested!</p>
            <p>If you have questions, please feel free to reach out any time at ${pO.pubSettings.emailUser}.</p>
            <p>- The ${pO.platName} Team</p>
          </html>
          """,
          s"""
          ${pO.uName}: The scene "${scene.name}" has been successfully ingested! If you have questions,
          please feel free to reach out any time at ${pO.pubSettings.emailUser}. - The ${pO.platName} Team
          """
        )
      case status: String if status == "FAILED" =>
        (
          s"${pO.platName}: Your scene ${scene.name} failed to ingest",
          s"""
          <html>
            <p>${pO.uName},</p><br>
            <p>The scene "${scene.name}" has failed to ingest. </p>
            <p>If you have questions, please feel free to reach out any time at ${pO.pubSettings.emailUser}.</p>
            <p>- The ${pO.platName} Team</p>
          </html>
          """,
          s"""
          ${pO.uName}: The scene "${scene.name}" has failed to ingest. If you have questions,
          please feel free to reach out any time at ${pO.pubSettings.emailUser}. - The ${pO.platName} Team
          """
        )
    }
  }

  def sendIngestStatusEmailToConsumers(platformsWithConsumers: List[PlatformWithUsersSceneProjects], scene: Scene, ingestStatus: String) =
    platformsWithConsumers.map(pU => {
      val email = new NotificationEmail

      (pU.emailNotifications, pU.pubSettings.emailIngestNotification) match {
        case (true, true) =>
          (pU.pubSettings.emailSmtpHost, pU.pubSettings.emailUser, pU.priSettings.emailPassword, pU.email) match {
            case (host: String, platUserEmail: String, pw: String, userEmail: String) if
              host.length != 0 && platUserEmail.length != 0 && pw.length != 0 && userEmail.length != 0 =>
              val (ingestEmailSubject, htmlBody, plainBody) = createIngestEmailContentForConsumers(pU, scene, ingestStatus)
              email.setEmail(host, 465, platUserEmail, pw, userEmail, ingestEmailSubject, htmlBody, plainBody).send()
            case (_, _, _, _) => logger.warn(email.insufficientSettingsWarning(pU.platId.toString(), pU.uId))
          }
        case (false, true) => logger.warn(email.userEmailNotificationDisabledWarning(pU.uId))
        case (true, false) => logger.warn(email.platformNotSubscribedWarning(pU.platId.toString()))
        case (false, false) => logger.warn(
          email.userEmailNotificationDisabledWarning(pU.uId) ++ " " ++ email.platformNotSubscribedWarning(pU.platId.toString()))
      }
    })

  def sendIngestStatusEmailToOwner(platformsWithOwnerO: Option[PlatformWithSceneOwner], scene: Scene, ingestStatus: String) = platformsWithOwnerO match {
    case Some(pO) =>
      val email = new NotificationEmail
      (pO.emailNotifications, pO.pubSettings.emailIngestNotification) match {
        case (true, true) =>
          (pO.pubSettings.emailSmtpHost, pO.pubSettings.emailUser, pO.priSettings.emailPassword, pO.email) match {
            case (host: String, platUserEmail: String, pw: String, userEmail: String) if
              host.length != 0 && platUserEmail.length != 0 && pw.length != 0 && userEmail.length != 0 =>
              val (ingestEmailSubject, htmlBody, plainBody) = createIngestEmailContentForOwner(pO, scene, ingestStatus)
              email.setEmail(host, 465, platUserEmail, pw, userEmail, ingestEmailSubject, htmlBody, plainBody).send()
            case (_, _, _, _) => logger.warn(email.insufficientSettingsWarning(pO.platId.toString(), pO.uId))
          }
        case (false, true) => logger.warn(email.userEmailNotificationDisabledWarning(pO.uId))
        case (true, false) => logger.warn(email.platformNotSubscribedWarning(pO.platId.toString()))
        case (false, false) => logger.warn(
          email.userEmailNotificationDisabledWarning(pO.uId) ++ " " ++ email.platformNotSubscribedWarning(pO.platId.toString()))
      }
    case _ => logger.warn("The scene owner might be the system user. Email not sent.")
  }


  def run: Unit = {
    val platformsWithConsumersAndSceneIO = for {
      consumers <- getSceneConsumers(sceneId)
      consumersFiltered = consumers.distinct.filter(_ != auth0Config.systemUser).map(_.toString)
      platformsWithConsumers <- PlatformDao.getPlatUsersAndProjByConsumerAndSceneID(consumersFiltered, sceneId)
      sceneO <- SceneDao.getSceneById(sceneId)
    } yield (platformsWithConsumers, sceneO)

    val platformsWithSceneOwnerIO = for {
      owner <- getSceneOwner(sceneId)
      platformsWithSceneOwner <- PlatformDao.getPlatAndUsersBySceneOwnerId(owner)
    } yield (platformsWithSceneOwner)

    val (platformsWithConsumers, sceneO) = platformsWithConsumersAndSceneIO.transact(xa).unsafeRunSync()
    val platformsWithSceneOwner = platformsWithSceneOwnerIO.transact(xa).unsafeRunSync()

    sceneO match {
      case Some(scene) =>
        scene.statusFields.ingestStatus.toString match {
          case ingestStatus: String if
            ingestStatus == "INGESTED" || ingestStatus == "FAILED" =>
              sendIngestStatusEmailToConsumers(platformsWithConsumers, scene, ingestStatus)
              sendIngestStatusEmailToOwner(platformsWithSceneOwner, scene, ingestStatus)
          case _ => logger.warn(s"Won't send an email unless the scene ${sceneId} is ingested or failed.")
        }
      case _ => logger.warn(s"No matched scene of id: ${sceneId}")
    }
    stop
  }
}

object NotifyIngestStatus extends LazyLogging {
  val name = "notify_ingest_status"

  def main(args: Array[String]): Unit = {
    implicit val xa = RFTransactor.xa

    val job = args.toList match {
      case List(id:String) => NotifyIngestStatus(UUID.fromString(id))
    }

    job.run
  }
}
