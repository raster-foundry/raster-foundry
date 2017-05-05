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

  /** Function to sum Reps of diff types */
  val sumTime: (Rep[String], Rep[Timestamp], Rep[Long]) => Rep[Timestamp] =
    SimpleFunction.ternary[String, Timestamp, Long, Timestamp]("+")

  def run: Unit = {
    logger.info(s"Updating Project $projectId AOI...")
    val result = for {
      user: User                          <- OptionT(Users.getUserById(airflowUser))
      (project: Project, aoi: AOI)        <- OptionT(Projects.getAOIProject(projectId, user))
      scenes: Iterable[Scene.WithRelated] <- OptionT({
        val area = aoi.area
        val queryParams = aoi.filters.as[CombinedSceneQueryParams].getOrElse(CombinedSceneQueryParams())

        val scenes: Future[Iterable[Scene.WithRelated]] = (for {
          sceneIds <- OptionT.liftF(
            database.db.run {
              Scenes
                .filterScenes(queryParams)
                .filter { _.dataFootprint.intersects(area) }
                .map { _.id }
                .result
            }
          )
          projects <- OptionT.liftF(Projects.addScenesToProject(sceneIds, projectId, user))
        } yield projects).value.map(_.getOrElse(Seq()))

        scenes.collect {
          case scenesSeq if scenesSeq.nonEmpty => {
            logger.info(s"Project $projectId is updated.")
            Projects.updateProject(project.copy(aoisLastChecked = Timestamp.from(ZonedDateTime.now.toInstant)), projectId, user)
            Some(scenesSeq)
          }
          case _ => {
            logger.info(s"Project $projectId update is not required.")
            None
          }
        }
      })
    } yield scenes

    val future: Future[Iterable[Scene.WithRelated]] = result.value.map { _.getOrElse(Seq()) }

    future onComplete {
      case Success(seq) if seq.nonEmpty => {
        logger.info(s"Updated Project $projectId with scenes: ${seq.mkString(",")}")
        stop
      }
      case Success(seq) if seq.isEmpty => {
        logger.info(s"Project $projectId required no update")
        stop
      }
      case Failure(e) => {
        sendError(e)
        stop
        throw e
      }
    }

    /*val r: Future[Option[Future[Iterable[Scene.WithRelated]]]] = Users.getUserById(airflowUser).flatMap { userOpt =>
      val user = userOpt.getOrElse {
        val e = new Exception(s"User $airflowUser doesn't exist.")
        sendError(e)
        stop
        throw e
      }

      Projects.getAOIProject(projectId, user).map { v => user -> v }
    }.map { case (user, ps) => ps.map { case (p, a) =>
      val area = a.area
      val queryParams = a.filters.as[CombinedSceneQueryParams].getOrElse(CombinedSceneQueryParams())

      val scenes: Future[Iterable[Scene.WithRelated]] = (for {
        sceneIds <- OptionT.liftF(
          database.db.run {
            Scenes
              .filterScenes(queryParams)
              .filter { _.dataFootprint.intersects(area) }
              .map { _.id }
              .result
          }
        )
        projects <- OptionT.liftF(Projects.addScenesToProject(sceneIds, projectId, user))
      } yield projects).value.map(_.getOrElse(Seq()))


      scenes.collect {
        case scenesSeq if scenesSeq.nonEmpty => {
          logger.info(s"Project $projectId is updated.")
          Projects.updateProject(p.copy(aoisLastChecked = Timestamp.from(ZonedDateTime.now.toInstant)), projectId, user)
          scenesSeq
        }
        case scenesSeq => {
          logger.info(s"Project $projectId update is not required.")
          scenesSeq
        }
      }
    } }*/

  }
}

object UpdateAOIProject {
  val name = "update_aoi_projects"

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

