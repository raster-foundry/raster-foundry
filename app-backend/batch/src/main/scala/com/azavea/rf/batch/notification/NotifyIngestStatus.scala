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

  def getSceneConsumers(v: UUID): ConnectionIO[List[String]] = {
    for {
      projects <- ProjectDao.query.filter(fr"id IN (SELECT project_id FROM scenes_to_projects WHERE scene_id = ${sceneId})").list
    } yield projects.map(_.owner)
  }

  def getSceneOwner(sceneId: UUID): ConnectionIO[String] = {
    for {
      scene <- SceneDao.query.filter(sceneId).select
    } yield scene.owner
  }

  def run: Unit = {
    val userIdsAndSceneIO = for {
      consumers <- getSceneConsumers(sceneId)
      owner <- getSceneOwner(sceneId)
      userIds = (owner :: consumers).distinct.filter(_ != auth0Config.systemUser).map(_.toString)
      sceneO <- SceneDao.getSceneById(sceneId)
    } yield (userIds, sceneO)

    val (userIds, sceneO) = userIdsAndSceneIO.transact(xa).unsafeRunSync()

    sceneO match {
      case Some(scene) =>
        val subject = s"Scene ${sceneId} Ingest Status Update"
        val content = scene.statusFields.ingestStatus.toString
        val email = NotificationEmail(userIds, subject, content, "ingest")
        email.sendEmail()
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
