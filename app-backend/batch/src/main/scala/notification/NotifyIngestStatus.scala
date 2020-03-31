package com.rasterfoundry.batch.notification

import com.rasterfoundry.batch.Job
import com.rasterfoundry.batch.util.conf.Config
import com.rasterfoundry.common.RollbarNotifier
import com.rasterfoundry.database.filter.Filterables._
import com.rasterfoundry.database.notification.Notify
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.database.{
  PlatformDao,
  ProjectDao,
  ProjectLayerDao,
  SceneDao
}
import com.rasterfoundry.notification.email.Model._
import com.rasterfoundry.datamodel._

import cats.effect.IO
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor

import java.util.UUID

final case class NotifyIngestStatus(sceneId: UUID)(
    implicit val xa: Transactor[IO]
) extends Config
    with RollbarNotifier {

  val name = NotifyIngestStatus.name

  def runJob(args: List[String]) = ???

  // We make sure that projectId isn't None in the filter, so it's safe to `.get`
  @SuppressWarnings(Array("OptionGet"))
  def getSceneConsumers(sceneId: UUID): ConnectionIO[List[String]] = {
    for {
      layers <- ProjectLayerDao.query
        .filter(
          fr"id IN (SELECT project_layer_id FROM scenes_to_layers WHERE scene_id = ${sceneId})"
        )
        .list
      projects <- layers.filter(!_.projectId.isEmpty).toNel match {
        case Some(nel) =>
          ProjectDao.query
            .filter(
              Fragments.in(fr"id", nel map { _.projectId.get })
            )
            .list
        case _ =>
          List.empty[Project].pure[ConnectionIO]
      }
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
      ingestStatus: String
  ): (Subject, HtmlBody, PlainBody) = {
    val platformHost =
      pU.pubSettings.platformHost.getOrElse("app.rasterfoundry.com")
    ingestStatus match {
      case status: String if status == "INGESTED" =>
        (
          Subject(
            s"${pU.platName}: Your ${pU.projectName}'s scene ${scene.name} is ready to view"
          ),
          HtmlBody(s"""
          <html>
            <p>${pU.uName},</p><br>
            <p>The scene "${scene.name}" has been successfully ingested into your project: ${pU.projectName}! You can access
            this project <a href="https://${platformHost}/projects/edit/${pU.projectId}/scenes">here</a> or any past
            projects you've created at any time <a href="https://${platformHost}/projects/">here</a>.</p>
            <p>If you have questions, support is available via in-app chat at ${platformHost} or less quickly via email to ${pU.pubSettings.emailSupport}.</p>
            <p>- The ${pU.platName} Team</p>
          </html>
          """),
          PlainBody(s"""
             | ${pU.uName}: The scene "${scene.name}" has been successfully ingested into your project: ${pU.projectName}!
             | You can access this project at here: https://${platformHost}/projects/edit/${pU.projectId}/scenes or
             | any past projects you've created at any time here: https://${platformHost}/projects/ . If you have
             | questions, support is available via in-app chat at ${platformHost} or less quickly via email to
             | ${pU.pubSettings.emailSupport}.
             | - The ${pU.platName} Team
          """.trim.stripMargin)
        )
      case status: String if status == "FAILED" =>
        (
          Subject(
            s"${pU.platName}: Your ${pU.projectName}'s scene ${scene.name} failed to ingest"
          ),
          HtmlBody(s"""
          <html>
            <p>${pU.uName},</p><br>
            <p>The scene "${scene.name}" in your project: ${pU.projectName} has failed to ingest. But you can access
            this project <a href="https://${platformHost}/projects/edit/${pU.projectId}/scenes">here</a> or any past
            projects you've created at any time <a href="https://${platformHost}/projects/">here</a>.</p>
            <p>If you have questions, support is available via in-app chat at ${platformHost} or less quickly via email to ${pU.pubSettings.emailSupport}.</p>
            <p>- The ${pU.platName} Team</p>
          </html>
          """),
          PlainBody(
            s"""
             | ${pU.uName}: The scene "${scene.name}" in your project: ${pU.projectName} has failed to ingest. But you can
             | access this project at here: https://${platformHost}/projects/edit/${pU.projectId}/scenes or
             | any past projects you've created at any time here: https://${platformHost}/projects/ . If you have
             | questions, support is available via in-app chat at ${platformHost} or less quickly via email to ${pU.pubSettings.emailSupport}.
             | - The ${pU.platName} Team
          """.trim.stripMargin
          )
        )
    }
  }

  def createIngestEmailContentForOwner(
      pO: PlatformWithSceneOwner,
      scene: Scene,
      ingestStatus: String
  ): (Subject, HtmlBody, PlainBody) = {
    val platformHost =
      pO.pubSettings.platformHost.getOrElse("app.rasterfoundry.com")
    ingestStatus match {
      case status: String if status == "INGESTED" =>
        (
          Subject(s"${pO.platName}: Your scene ${scene.name} is ready to view"),
          HtmlBody(s"""
          <html>
            <p>${pO.uName},</p><br>
            <p>The scene "${scene.name}" has been successfully ingested!</p>
            <p>If you have questions, support is available via in-app chat at ${platformHost} or less quickly via email to ${pO.pubSettings.emailSupport}.</p>
            <p>- The ${pO.platName} Team</p>
          </html>
          """),
          PlainBody(
            s"""
             | ${pO.uName}: The scene "${scene.name}" has been successfully ingested!
             | If you have questions, support is available via in-app chat at ${platformHost} or less quickly via email to ${pO.pubSettings.emailSupport}.
             | - The ${pO.platName} Team
          """.trim.stripMargin
          )
        )
      case status: String if status == "FAILED" =>
        (
          Subject(s"${pO.platName}: Your scene ${scene.name} failed to ingest"),
          HtmlBody(s"""
          <html>
            <p>${pO.uName},</p><br>
            <p>The scene "${scene.name}" has failed to ingest. </p>
            <p>If you have questions, support is available via in-app chat at ${platformHost} or less quickly via email to ${pO.pubSettings.emailSupport}.</p>
            <p>- The ${pO.platName} Team</p>
          </html>
          """),
          PlainBody(s"""
             | ${pO.uName}: The scene "${scene.name}" has failed to ingest.
             | If you have questions,
             | support is available via in-app chat at ${platformHost} or less quickly via email to ${pO.pubSettings.emailSupport}.
             | - The ${pO.platName} Team
          """.trim.stripMargin)
        )
    }
  }

  def sendIngestStatusEmailToConsumers(
      platformsWithConsumers: List[PlatformWithUsersSceneProjects],
      scene: Scene,
      ingestStatus: String
  ): IO[Unit] =
    (platformsWithConsumers traverse { pU =>
      (pU.getUserEmail, pU.pubSettings.emailIngestNotification) match {
        case ("", true) =>
          IO {
            logger.warn(
              s"User ${pU.uId} disabled email notifications."
            )
          }
        case ("", false) =>
          IO {
            logger.warn(
              s"platform ${pU.platId} has not enabled email notifications"
            )
          }
        case (userEmailAddress, true) =>
          val (ingestEmailSubject, htmlBody, plainBody) =
            createIngestEmailContentForConsumers(pU, scene, ingestStatus)
          Notify.sendEmail(
            pU.pubSettings,
            pU.priSettings,
            userEmailAddress,
            ingestEmailSubject.underlying,
            htmlBody.underlying,
            plainBody.underlying
          ) map { _ =>
            logger.info(s"Notified project owner ${pU.uId}.")
          }
        case (_, false) =>
          IO {
            logger.warn(
              s"platform ${pU.platId} has not enabled email notifications"
            )
          }
      }
    }).void

  def sendIngestStatusEmailToOwner(
      pO: PlatformWithSceneOwner,
      scene: Scene,
      ingestStatus: String
  ): IO[Unit] = {
    (pO.getUserEmail, pO.pubSettings.emailIngestNotification) match {
      case ("", _) =>
        IO {
          logger.warn(
            s"User ${pO.uId} disabled email notifications."
          )
        }
      case (userEmailAddress, true) =>
        val (ingestEmailSubject, htmlBody, plainBody) =
          createIngestEmailContentForOwner(pO, scene, ingestStatus)
        Notify.sendEmail(
          pO.pubSettings,
          pO.priSettings,
          userEmailAddress,
          ingestEmailSubject.underlying,
          htmlBody.underlying,
          plainBody.underlying
        ) map { _ =>
          logger.info(s"Notified project owner ${pO.uId}.")
        }
      case (_, false) =>
        IO {
          logger.warn(
            s"platform ${pO.platId} has not enabled email notifications"
          )
        }
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
        sendIngestStatusEmailToConsumers(
          platformsWithConsumers,
          scene,
          ingestStatus
        )
      case _ => logger.warn(s"Scene ${sceneId} is not in any project yet.")
    }
    ()
  }

  def notifyOwners(scene: Scene, ingestStatus: String): IO[Unit] = {
    logger.info("Notifying owner...")

    if (scene.owner == auth0Config.systemUser) {
      IO {
        logger.warn(
          s"Owner of scene ${sceneId} is a system user. Email not sent."
        )
      }
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
      s"Notifying owner and consumer about ingest status for scene ${sceneId}..."
    )

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
              s"Won't send an email unless the scene ${sceneId} is ingested or failed."
            )
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
