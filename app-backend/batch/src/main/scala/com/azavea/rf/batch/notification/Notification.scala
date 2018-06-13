package com.azavea.rf.batch.notification

import com.auth0.client.mgmt._
import com.auth0.client.mgmt.filter.UserFilter
import com.auth0.client.auth._
import com.auth0.json.auth.TokenHolder
import com.typesafe.scalalogging.LazyLogging
import java.util.UUID

import cats.effect.IO
import org.apache.commons.mail._;
import org.apache.commons.mail.Email._;

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
    logger.info("in set email")
    logger.info(s"${host}, ${port}, ${uName}, ${uPw}, ${subject}, ${msg}, ${to}")
    val email = new SimpleEmail()
    email.setDebug(true)

    email.setHostName(host)
    logger.info(email.getHostName())

    email.setSmtpPort(port)
    logger.info(email.getSmtpPort())

    email.setAuthentication(uName, uPw)

    email.setSSLOnConnect(false)

    email.setFrom(uName)
    logger.info(email.getFromAddress().toString)

    email.setSubject(subject)
    logger.info(email.getSubject())

    email.setMsg(msg)

    email.addTo(to)
    logger.info(email.getToAddresses().toString)

    logger.info(email.toString)

    email
  }

  def run: Unit = {
    logger.info("in run")
    // val authApi = new AuthAPI(auth0Config.domain, auth0Config.clientId, auth0Config.clientSecret)
    // val mgmtTokenRequest = authApi.requestToken(s"https://${auth0Config.domain}/api/v2/")
    // val mgmtToken = mgmtTokenRequest.execute
    // val mgmtApi = new ManagementAPI(auth0Config.domain, mgmtToken.getAccessToken)

    val platformsWithUsersAndSceneIO = for {
      consumers <- getSceneConsumers(sceneId)
      owner <- getSceneOwner(sceneId)
      userIds = (owner :: consumers).distinct.filter(_ != auth0Config.systemUser).map(_.toString)
      platformsWithUsers <- PlatformDao.getPlatformsAndUsersByUsersId(userIds)
      sceneO <- SceneDao.getSceneById(sceneId)
    } yield (platformsWithUsers, sceneO, userIds)

    val (platformsWithUsers, sceneO, userIds) = platformsWithUsersAndSceneIO.transact(xa).unsafeRunSync()

    logger.info(userIds.mkString(", "))

    logger.info("after transaction")


    platformsWithUsers.map(pU => {
      logger.info(pU.toString)
      sceneO match {
        case Some(scene) =>
          val subject = s"Scene ${sceneId} Ingest Status Update"
          val msg = scene.statusFields.ingestStatus.toString
          logger.info(s"${msg}, ${subject}")
          logger.info(s"${pU.email}, ${pU.emailNotifications}, ${pU.pubSettings}, ${pU.priSettings}")
          val email = setEmail(
            "smtp.mailtrap.io",
            25,
            "d0f7603b04a07c",
            "b16a364057e19a",
            subject,
            msg,
            "xunzesu@outlook.com")
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
