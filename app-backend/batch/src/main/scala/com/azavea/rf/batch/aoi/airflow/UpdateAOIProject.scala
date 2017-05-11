package com.azavea.rf.batch.aoi.airflow

import com.azavea.rf.batch.Job
import com.azavea.rf.database.tables._
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.database.query._
import com.azavea.rf.datamodel._

import cats.data._
import cats.implicits._

import java.sql.Timestamp
import java.time.ZonedDateTime
import java.util.UUID

import scala.concurrent.Future
import scala.util.{Failure, Success}

case class UpdateAOIProject(projectId: UUID)(implicit val database: DB) extends Job {
  val name = FindAOIProjects.name

  import database.driver.api._

  def run: Unit = {
    logger.info(s"Updating Project $projectId AOI...")
    val result = for {
      user: User                   <- OptionT(Users.getUserById(airflowUser))
      (project: Project, aoi: AOI) <- OptionT(Projects.getAOIProject(projectId, user))
      scenes: Iterable[UUID]       <- OptionT({
        val area = aoi.area
        val queryParams =
          aoi
            .filters.as[CombinedSceneQueryParams]
            .getOrElse(throw new Exception(s"No valid queryParams passed: ${aoi.filters}"))

        val scenes: Future[Iterable[UUID]] = (for {
          sceneIds <- OptionT.liftF(
            database.db.run {
              Scenes
                .filterScenes(queryParams)
                .filterUserVisibility(user)
                .filter { s => s.dataFootprint.intersects(area) && s.createdAt > project.aoisLastChecked }
                .map { _.id }
                .result
            }
          )
          projects <- OptionT.liftF(Projects.addScenesToProject(sceneIds, projectId, user))
        } yield projects.map(_.id)).value.map(_.getOrElse(Seq()))

        scenes.map { scenesSeq => {
          logger.info(s"Project $projectId is updated.")
          Projects.updateProject(project.copy(aoisLastChecked = Timestamp.from(ZonedDateTime.now.toInstant)), projectId, user)
          scenesSeq.some
        } }
      })
    } yield scenes

    val future: Future[Iterable[UUID]] = result.value.map { _.getOrElse(Seq()) }

    future onComplete {
      case Success(seq) => {
        if(seq.nonEmpty) logger.info(s"Updated Project $projectId with scenes: ${seq.mkString(",")}")
        else logger.info(s"Project $projectId required no update")
        stop
      }
      case Failure(e) => {
        e.printStackTrace()
        sendError(e)
        stop
        sys.exit(1)
      }
    }
  }
}

object UpdateAOIProject {
  val name = "update_aoi_project"

  def main(args: Array[String]): Unit = {
    implicit val db = DB.DEFAULT

    val job = args.toList match {
      case List(projectId) => UpdateAOIProject(UUID.fromString(projectId))
      case _ =>
        throw new IllegalArgumentException("Argument could not be parsed to UUID")
    }

    job.run
  }
}
