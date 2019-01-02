package com.rasterfoundry.batch.notification

import java.util.UUID

import cats.effect.IO
import cats.implicits._
import com.rasterfoundry.batch.Job
import com.rasterfoundry.batch.util.conf.Config
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.common.notification.Email.NotificationEmail
import com.rasterfoundry.database.filter.Filterables._
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.database.{PlatformDao, ProjectDao, SceneDao}
import com.rasterfoundry.datamodel._
import com.typesafe.scalalogging.LazyLogging
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import org.apache.commons.mail.Email

final case class NotifyIngestStatus(sceneId: UUID)(
    implicit val xa: Transactor[IO])
    extends Config
    with RollbarNotifier {

  val name = NotifyIngestStatus.name

  def runJob(args: List[String]) = ???

  def getSceneConsumers(sceneId: UUID): ConnectionIO[List[String]] = {
    for {
      projects <- ProjectDao.query
        .filter(
          fr"id IN (SELECT project_id FROM scenes_to_projects WHERE scene_id = ${sceneId})")
        .list
    } yield
      projects
        .map(_.owner)
        .distinct
        .filter(_ != auth0Config.systemUser)
        .map(_.mkString)
  }

  def createIngestEmailContentForConsumers(
      pU: PlatformWithUsersSceneProjects,
      scene: Scene,
      ingestStatus: String): (String, String, String) = {
    val platformHost =
      pU.pubSettings.platformHost.getOrElse("app.rasterfoundry.com")
    ingestStatus match {
      case status: String if status == "INGESTED" =>
        (
          s"${pU.platName}: Your ${pU.projectName}'s scene ${scene.name} is ready to view",
          s"""
          <html>
            <p>${pU.uName},</p><br>
            <p>The scene "${scene.name}" has been successfully ingested into your project: ${pU.projectName}! You can access
            this project <a href="https://${platformHost}/projects/edit/${pU.projectId}/scenes">here</a> or any past
            projects you've created at any time <a href="https://${platformHost}/projects/">here</a>.</p>
            <p>If you have questions, support is available via in-app chat at ${platformHost} or less quickly via email to ${pU.pubSettings.emailSupport}.</p>
            <p>- The ${pU.platName} Team</p>
          </html>
          """,
          s"""
             | ${pU.uName}: The scene "${scene.name}" has been successfully ingested into your project: ${pU.projectName}!
             | You can access this project at here: https://${platformHost}/projects/edit/${pU.projectId}/scenes or
             | any past projects you've created at any time here: https://${platformHost}/projects/ . If you have
             | questions, support is available via in-app chat at ${platformHost} or less quickly via email to
             | ${pU.pubSettings.emailSupport}.
             | - The ${pU.platName} Team
          """.trim.stripMargin
        )
      case status: String if status == "FAILED" =>
        (
          s"${pU.platName}: Your ${pU.projectName}'s scene ${scene.name} failed to ingest",
          s"""
          <html>
            <p>${pU.uName},</p><br>
            <p>The scene "${scene.name}" in your project: ${pU.projectName} has failed to ingest. But you can access
            this project <a href="https://${platformHost}/projects/edit/${pU.projectId}/scenes">here</a> or any past
            projects you've created at any time <a href="https://${platformHost}/projects/">here</a>.</p>
            <p>If you have questions, support is available via in-app chat at ${platformHost} or less quickly via email to ${pU.pubSettings.emailSupport}.</p>
            <p>- The ${pU.platName} Team</p>
          </html>
          """,
          s"""
             | ${pU.uName}: The scene "${scene.name}" in your project: ${pU.projectName} has failed to ingest. But you can
             | access this project at here: https://${platformHost}/projects/edit/${pU.projectId}/scenes or
             | any past projects you've created at any time here: https://${platformHost}/projects/ . If you have
             | questions, support is available via in-app chat at ${platformHost} or less quickly via email to ${pU.pubSettings.emailSupport}.
             | - The ${pU.platName} Team
          """.trim.stripMargin
        )
    }
  }

  def createIngestEmailContentForOwner(
      pO: PlatformWithSceneOwner,
      scene: Scene,
      ingestStatus: String): (String, String, String) = {
    val platformHost =
      pO.pubSettings.platformHost.getOrElse("app.rasterfoundry.com")
    ingestStatus match {
      case status: String if status == "INGESTED" =>
        (
          s"${pO.platName}: Your scene ${scene.name} is ready to view",
          s"""
          <html>
            <p>${pO.uName},</p><br>
            <p>The scene "${scene.name}" has been successfully ingested!</p>
            <p>If you have questions, support is available via in-app chat at ${platformHost} or less quickly via email to ${pO.pubSettings.emailSupport}.</p>
            <p>- The ${pO.platName} Team</p>
          </html>
          """,
          s"""
             | ${pO.uName}: The scene "${scene.name}" has been successfully ingested!
             | If you have questions, support is available via in-app chat at ${platformHost} or less quickly via email to ${pO.pubSettings.emailSupport}.
             | - The ${pO.platName} Team
          """.trim.stripMargin
        )
      case status: String if status == "FAILED" =>
        (
          s"${pO.platName}: Your scene ${scene.name} failed to ingest",
          s"""
          <html>
            <p>${pO.uName},</p><br>
            <p>The scene "${scene.name}" has failed to ingest. </p>
            <p>If you have questions, support is available via in-app chat at ${platformHost} or less quickly via email to ${pO.pubSettings.emailSupport}.</p>
            <p>- The ${pO.platName} Team</p>
          </html>
          """,
          s"""
             | ${pO.uName}: The scene "${scene.name}" has failed to ingest.
             | If you have questions,
             | support is available via in-app chat at ${platformHost} or less quickly via email to ${pO.pubSettings.emailSupport}.
             | - The ${pO.platName} Team
          """.trim.stripMargin
        )
    }
  }

  def sendIngestStatusEmailToConsumers(
      platformsWithConsumers: List[PlatformWithUsersSceneProjects],
      scene: Scene,
      ingestStatus: String) =
    platformsWithConsumers.map(pU => {
      val email = new NotificationEmail

      (pU.getUserEmail, pU.pubSettings.emailIngestNotification) match {
        case ("", true) =>
          logger.warn(email.userEmailNotificationDisabledWarning(pU.uId))
        case ("", false) =>
          logger.warn(
            email.userEmailNotificationDisabledWarning(pU.uId) ++ " " ++ email
              .platformNotSubscribedWarning(pU.platId.toString()))
        case (userEmailAddress, true) =>
          (pU.pubSettings.emailSmtpHost,
           pU.pubSettings.emailSmtpPort,
           pU.pubSettings.emailSmtpEncryption,
           pU.pubSettings.emailSmtpUserName,
           pU.priSettings.emailPassword,
           userEmailAddress) match {
            case (host: String,
                  port: Int,
                  encryption: String,
                  platSmtpUserName: String,
                  pw: String,
                  userEmail: String)
                if email.isValidEmailSettings(host,
                                              port,
                                              encryption,
                                              platSmtpUserName,
                                              pw,
                                              userEmail) =>
              val (ingestEmailSubject, htmlBody, plainBody) =
                createIngestEmailContentForConsumers(pU, scene, ingestStatus)
              email
                .setEmail(host,
                          port,
                          encryption,
                          platSmtpUserName,
                          pw,
                          userEmail,
                          ingestEmailSubject,
                          htmlBody,
                          plainBody,
                          pU.pubSettings.emailFrom,
                          pU.pubSettings.emailFromDisplayName)
                .map((configuredEmail: Email) => configuredEmail.send)
              logger.info(s"Notified project owner ${pU.uId}.")
            case _ =>
              logger.warn(
                email.insufficientSettingsWarning(pU.platId.toString(), pU.uId))
          }
        case (_, false) =>
          logger.warn(email.platformNotSubscribedWarning(pU.platId.toString()))
      }
    })

  def sendIngestStatusEmailToOwner(pO: PlatformWithSceneOwner,
                                   scene: Scene,
                                   ingestStatus: String) = {
    val email = new NotificationEmail

    (pO.getUserEmail, pO.pubSettings.emailIngestNotification) match {
      case ("", true) =>
        logger.warn(email.userEmailNotificationDisabledWarning(pO.uId))
      case ("", false) =>
        logger.warn(
          email.userEmailNotificationDisabledWarning(pO.uId) ++ " " ++ email
            .platformNotSubscribedWarning(pO.platId.toString()))
      case (userEmailAddress, true) =>
        (pO.pubSettings.emailSmtpHost,
         pO.pubSettings.emailSmtpPort,
         pO.pubSettings.emailSmtpEncryption,
         pO.pubSettings.emailSmtpUserName,
         pO.priSettings.emailPassword,
         userEmailAddress) match {
          case (host: String,
                port: Int,
                encryption: String,
                platSmtpUserName: String,
                pw: String,
                userEmail: String)
              if email.isValidEmailSettings(host,
                                            port,
                                            encryption,
                                            platSmtpUserName,
                                            pw,
                                            userEmail) =>
            val (ingestEmailSubject, htmlBody, plainBody) =
              createIngestEmailContentForOwner(pO, scene, ingestStatus)
            email
              .setEmail(host,
                        port,
                        encryption,
                        platSmtpUserName,
                        pw,
                        userEmail,
                        ingestEmailSubject,
                        htmlBody,
                        plainBody,
                        pO.pubSettings.emailFrom,
                        pO.pubSettings.emailFromDisplayName)
              .map((configuredEmail: Email) => configuredEmail.send)
            logger.info(s"Notified scene owner ${pO.uId}.")
          case _ =>
            logger.warn(
              email.insufficientSettingsWarning(pO.platId.toString(), pO.uId))
        }
      case (_, false) =>
        logger.warn(email.platformNotSubscribedWarning(pO.platId.toString()))
    }
  }

  def notifyConsumers(scene: Scene, ingestStatus: String): Unit = {
    logger.info("Notifying Consumers...")

    val consumerIdsO: List[String] =
      getSceneConsumers(sceneId).transact(xa).unsafeRunSync()

    consumerIdsO match {
      case consumerIds: List[String] if consumerIds.nonEmpty =>
        val platformsWithConsumers = PlatformDao
          .getPlatUsersAndProjByConsumerAndSceneID(consumerIds, sceneId)
          .transact(xa)
          .unsafeRunSync()
        sendIngestStatusEmailToConsumers(platformsWithConsumers,
                                         scene,
                                         ingestStatus)
      case _ => logger.warn(s"Scene ${sceneId} is not in any project yet.")
    }
  }

  def notifyOwners(scene: Scene, ingestStatus: String): Unit = {
    logger.info("Notifying owner...")

    if (scene.owner == auth0Config.systemUser) {
      logger.warn(
        s"Owner of scene ${sceneId} is a system user. Email not sent.")
    } else {
      val platformsWithSceneOwner = PlatformDao
        .getPlatAndUsersBySceneOwnerId(scene.owner)
        .transact(xa)
        .unsafeRunSync()
      sendIngestStatusEmailToOwner(platformsWithSceneOwner, scene, ingestStatus)
    }
  }

  def run(): Unit = {

    logger.info(
      s"Notifying owner and consumer about ingest status for scene ${sceneId}...")

    val sceneIO = for {
      sceneO <- SceneDao.getSceneById(sceneId)
    } yield sceneO

    val sceneO = sceneIO.transact(xa).unsafeRunSync()

    sceneO match {
      case Some(scene) =>
        scene.statusFields.ingestStatus.toString match {
          case ingestStatus: String
              if ingestStatus == "INGESTED" || ingestStatus == "FAILED" =>
            notifyOwners(scene, ingestStatus)
            notifyConsumers(scene, ingestStatus)
          case _ =>
            logger.warn(
              s"Won't send an email unless the scene ${sceneId} is ingested or failed.")
        }
      case _ => logger.warn(s"No matched scene of id: ${sceneId}")
    }
  }
}

object NotifyIngestStatus extends Job {
  val name = "notify_ingest_status"

  def runJob(args: List[String]): IO[Unit] = {
    RFTransactor.xaResource.use(transactor => {
      implicit val xa = transactor
      val job = args.toList match {
        case List(id: String) => NotifyIngestStatus(UUID.fromString(id))
      }

      IO { job.run }
    })
  }
}
