package com.azavea.rf.batch.notification

import com.auth0.client.mgmt._
import com.auth0.client.mgmt.filter.UserFilter
import com.auth0.client.auth._
import com.auth0.json.auth.TokenHolder
import com.typesafe.scalalogging.LazyLogging
import java.util.UUID
import scala.concurrent.Future

import com.azavea.rf.batch.Job
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.tables.{ScenesToProjects, Projects, Scenes}
import com.azavea.rf.datamodel._
import com.azavea.rf.common.{RollbarNotifier, S3}

case class NotifyIngestStatus(sceneId: UUID)(implicit val database: DB) extends Job
  with RollbarNotifier {

  val name = NotifyIngestStatus.name

  def getSceneConsumers(sceneId: UUID): Future[Seq[String]] = {
    ScenesToProjects.allProjects(sceneId) flatMap { pids: Seq[UUID] =>
      Future.sequence {
        pids.map { pid: UUID =>
          Projects.getProjectPreauthorized(pid) map {
            _.getOrElse {
              throw new Exception(s"Project $pid (consuming $sceneId) has no owner to notify")
            }.owner
          }
        }
      }
    }
  }

  def getSceneOwner(sceneId: UUID): Future[String] = {
    Scenes.getScenePreauthorized(sceneId) flatMap { scene: Option[Scene.WithRelated] =>
      Future {
        scene.getOrElse {
          throw new Exception(s"Scene $sceneId has no owner to notify")
        }.owner
      }
    }
  }

  def run: Unit = {
    val authApi = new AuthAPI(auth0Config.domain, auth0Config.clientId, auth0Config.clientSecret)
    val mgmtTokenRequest = authApi.requestToken(s"https://${auth0Config.domain}/api/v2/")
    val mgmtToken = mgmtTokenRequest.execute
    val mgmtApi = new ManagementAPI(auth0Config.domain, mgmtToken.getAccessToken)

    for {
      consumers <- getSceneConsumers(sceneId)
      owner <- getSceneOwner(sceneId)
    } yield {
      (owner +: consumers)
        .distinct
        .filter(_ != auth0Config.systemUser)
        .map { uid: String =>
          try {
            val user = mgmtApi.users().get(uid, new UserFilter()).execute()
            logger.info(s"Notification stub - ${uid} -> ${user.getEmail}")
          } catch {
            case e: Throwable => {
              sendError(e)
              logger.warn(s"No user found for this id: ${uid}")
            }
          }
        }
      stop
    }
  }
}

object NotifyIngestStatus extends LazyLogging {
  val name = "notify_ingest_status"

  def main(args: Array[String]): Unit = {
    implicit val db = DB.DEFAULT

    val job = args.toList match {
      case List(id:String) => NotifyIngestStatus(UUID.fromString(id))
    }

    job.run
  }
}
