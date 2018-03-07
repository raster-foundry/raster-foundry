package com.azavea.rf.batch.aoi

import com.auth0.client.mgmt._
import com.auth0.client.mgmt.filter.UserFilter
import com.auth0.client.auth._
import com.auth0.json.auth.TokenHolder
import com.azavea.rf.batch.Job
import com.azavea.rf.database.tables._
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.datamodel._
import cats.data._
import cats.implicits._
import java.sql.Timestamp
import java.time.ZonedDateTime
import java.util.UUID

import cats.effect.IO
import doobie.util.transactor.Transactor

import scala.concurrent.Future
import scala.util.{Failure, Success}

case class UpdateAOIProject(projectId: UUID)(implicit val xa: Transactor[IO]) extends Job {
  val name = FindAOIProjects.name


  def run: Unit = {
//    val authApi = new AuthAPI(auth0Config.domain, auth0Config.clientId, auth0Config.clientSecret)
//    val mgmtTokenRequest = authApi.requestToken(s"https://${auth0Config.domain}/api/v2/")
//    val mgmtToken = mgmtTokenRequest.execute
//    val mgmtApi = new ManagementAPI(auth0Config.domain, mgmtToken.getAccessToken)
//
//    logger.info(s"Updating Project $projectId AOI...")
//    val result = for {
//      user: User                   <- OptionT(Users.getUserById(systemUser))
//      (project: Project, aoi: AOI) <- OptionT(Projects.getAOIProject(projectId, user))
//      scenes: Iterable[UUID]       <- OptionT(
//        {
//          val area = aoi.area
//          val queryParams =
//            aoi
//              .filters.as[CombinedSceneQueryParams]
//              .getOrElse(throw new Exception(s"No valid queryParams passed: ${aoi.filters}"))
//
//          val scenes: Future[Iterable[UUID]] = (
//            for {
//              sceneIds <- OptionT.liftF(
//                database.db.run {
//                  Scenes
//                    .filterScenes(queryParams)
//                    .filterUserVisibility(user)
//                    .filter { s => s.dataFootprint.intersects(area) && s.createdAt > project.aoisLastChecked }
//                    .map { _.id }
//                    .result
//                }
//              )
//              projects <- OptionT.liftF(Projects.addScenesToProject(sceneIds, projectId, user))
//            } yield projects.map(_.id)).value.map(_.getOrElse(Seq()))
//
//
//          scenes.map {
//            scenesSeq => {
//              logger.info(s"Project $projectId is updated.")
//              Projects.updateProject(
//                project.copy(aoisLastChecked = Timestamp.from(ZonedDateTime.now.toInstant)),
//                projectId,
//                user
//              )
//              scenesSeq.some
//            }
//          }
//        }
//      )
//    } yield (scenes, project)
//
//    val future: Future[Iterable[UUID]] = result.value.map {
//      (option: Option[(Iterable[UUID], Project)]) =>
//      option match {
//        case Some((scenes: Iterable[UUID], project: Project)) =>
//          val user = mgmtApi.users().get(project.owner, new UserFilter()).execute()
//          logger.info(s"Notifications stub - ${project.owner} -> ${user.getEmail}")
//          scenes
//        case _ =>
//          Seq()
//      }
//    }
//
//    future onComplete {
//      case Success(seq) => {
//        if(seq.nonEmpty) logger.info(s"Updated Project $projectId with scenes: ${seq.mkString(",")}")
//        else logger.info(s"Project $projectId required no update")
//        stop
//      }
//      case Failure(e) => {
//        e.printStackTrace()
//        sendError(e)
//        stop
//        sys.exit(1)
//      }
//    }
  }
}

object UpdateAOIProject {
  val name = "update_aoi_project"

  def main(args: Array[String]): Unit = {
//    implicit val db = DB.DEFAULT
//
//    val job = args.toList match {
//      case List(projectId) => UpdateAOIProject(UUID.fromString(projectId))
//      case _ =>
//        throw new IllegalArgumentException("Argument could not be parsed to UUID")
//    }
//
//    job.run
  }
}
