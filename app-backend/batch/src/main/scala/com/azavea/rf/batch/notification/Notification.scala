package com.azavea.rf.batch.notification

import com.auth0.client.mgmt._
import com.auth0.client.mgmt.filter.UserFilter
import com.auth0.client.auth._
import com.auth0.json.auth.TokenHolder
import com.typesafe.scalalogging.LazyLogging
import java.util.UUID

import cats.effect.IO
import org.apache.commons.mail._
import org.apache.commons.mail.Email._

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

  logger.info("In nottifications")
  val name = NotifyIngestStatus.name

  def getSceneConsumers(sceneId: UUID): ConnectionIO[List[String]] = {
    for {
      projects <- ProjectDao.query.filter(fr"id IN (SELECT project_id FROM scenes_to_projects WHERE scene_id = ${sceneId})").list
    } yield projects.map(_.owner)
  }

  def getSceneOwner(sceneId: UUID): ConnectionIO[String] = {
    for {
      scene <- SceneDao.query.filter(sceneId).select
    } yield scene.owner
  }

  def setEmail(host: String, port: Int, uName: String, uPw: String, subject: String, msg: String, to: String): Email = {
    val email = new SimpleEmail()
    email.setDebug(true)
    email.setHostName(host)
    email.setSSL(true)
    email.setSslSmtpPort(port.toString)
    email.setAuthenticator(new DefaultAuthenticator(uName, uPw))
    email.setFrom(uName)
    email.setSubject(subject)
    email.setMsg(msg)
    email.addTo(to)
    email
  }

  def run: Unit = {
    val platformsWithUsersAndSceneIO = for {
      consumers <- getSceneConsumers(sceneId)
      owner <- getSceneOwner(sceneId)
      userIds = (owner :: consumers).distinct.filter(_ != auth0Config.systemUser).map(_.toString)
      platformsWithUsers <- PlatformDao.getPlatformsAndUsersByUsersId(userIds)
      sceneO <- SceneDao.getSceneById(sceneId)
    } yield (platformsWithUsers, sceneO, userIds)

    val (platformsWithUsers, sceneO, userIds) = platformsWithUsersAndSceneIO.transact(xa).unsafeRunSync()

    platformsWithUsers.map(pU => {
      logger.info(pU.toString)
      sceneO match {
        case Some(scene) =>
          val subject = s"Scene ${sceneId} Ingest Status Update"
          val msg = scene.statusFields.ingestStatus.toString
          val email = setEmail(
            pU.pubSettings.emailSmtpHost,
            465,
            pU.pubSettings.emailUser,
            pU.priSettings.emailPassword,
            subject,
            msg,
            pU.email
          )
          email.send()
        case _ => logger.warn(s"No matched scene of id: ${sceneId}")
      }
    })
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
