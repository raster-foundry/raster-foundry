package com.azavea.rf.batch.notification

import com.auth0.client.mgmt._
import com.auth0.client.mgmt.filter.UserFilter
import com.auth0.client.auth._
import com.auth0.json.auth.TokenHolder
import com.typesafe.scalalogging.LazyLogging
import java.util.UUID

import cats.effect.IO


import scala.concurrent.Future
import com.azavea.rf.batch.Job
import com.azavea.rf.datamodel._
import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.common.{RollbarNotifier, S3}
import com.azavea.rf.database.{ProjectDao, SceneDao}
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
      projects <- ProjectDao.query.filter(fr"id IN (SELECT project_id FROM scenes_to_projects WHERE scene_id = ${sceneId}").list
    } yield projects.map(_.owner)
  }

  def getSceneOwner(sceneId: UUID): ConnectionIO[String] = {
    for {
      scene <- SceneDao.query.filter(fr"id = ${sceneId}").select
    } yield scene.owner
  }

  def run: Unit = {
    val authApi = new AuthAPI(auth0Config.domain, auth0Config.clientId, auth0Config.clientSecret)
    val mgmtTokenRequest = authApi.requestToken(s"https://${auth0Config.domain}/api/v2/")
    val mgmtToken = mgmtTokenRequest.execute
    val mgmtApi = new ManagementAPI(auth0Config.domain, mgmtToken.getAccessToken)

    val notificationStrings = for {
      consumers <- getSceneConsumers(sceneId)
      owner <- getSceneOwner(sceneId)
    } yield {
      (owner +: consumers)
        .distinct
        .filter(_ != auth0Config.systemUser)
        .map { uid: String =>
          try {
            val user = mgmtApi.users().get(uid, new UserFilter()).execute()
            Some(s"Notification stub - ${uid} -> ${user.getEmail}")
          } catch {
            case e: Throwable => {
              sendError(e)
              logger.warn(s"No user found for this id: ${uid}")
              None
            }
          }
        }
    }
    notificationStrings.transact(xa).unsafeRunSync().map {
      case Some(s) => logger.info(s)
      case _ => None
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
