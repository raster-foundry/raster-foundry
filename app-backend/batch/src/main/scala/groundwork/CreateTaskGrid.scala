package com.rasterfoundry.batch.groundwork

import com.rasterfoundry.batch.Job
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.database.{
  AnnotationProjectDao,
  ProjectDao,
  TaskDao,
  UserDao
}
import com.rasterfoundry.datamodel.{Task, TaskStatus}

import cats.data.OptionT
import cats.effect.{IO, LiftIO}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import doobie.{ConnectionIO, Transactor}
import doobie.implicits._

import java.util.UUID

class CreateTaskGrid(
    annotationProjectId: UUID,
    taskSizeMeters: Double,
    xa: Transactor[IO]
) extends LazyLogging {

  private def debug(s: String): ConnectionIO[Unit] =
    LiftIO[ConnectionIO].liftIO(
      IO { logger.debug(s) }
    )

  def run(): IO[Unit] =
    (for {
      annotationProject <- OptionT {
        AnnotationProjectDao.getProjectById(annotationProjectId)
      }
      _ <- OptionT.liftF {
        debug(s"Got annotation project ${annotationProject.name}")
      }
      owner <- OptionT {
        UserDao.getUserById(annotationProject.createdBy)
      }
      footprint <- OptionT {
        annotationProject.projectId traverse { projectId =>
          ProjectDao.getFootprint(projectId)
        }
      } <* OptionT.liftF {
        debug("Got annotation project footprint")
      }
      taskGridFeatureCreate = Task.TaskGridFeatureCreate(
        Task.TaskGridCreateProperties(Some(taskSizeMeters)),
        footprint
      )
      taskProperties = Task.TaskPropertiesCreate(
        TaskStatus.Unlabeled,
        annotationProject.id
      )
      _ <- OptionT.liftF {
        TaskDao
          .insertTasksByGrid(taskProperties, taskGridFeatureCreate, owner)
      }
      _ <- OptionT.liftF { debug("Inserted tasks") }
      _ <- OptionT.liftF {
        AnnotationProjectDao
          .update(
            annotationProject.copy(
              aoi = footprint,
              ready = true,
              taskSizeMeters = Some(taskSizeMeters)
            ),
            annotationProject.id
          )
      }
      _ <- OptionT.liftF { debug("Updated annotation project") }
    } yield ()).value.transact(xa).void
}

object CreateTaskGrid extends Job {

  val name = "create-task-grid"

  def runJob(args: List[String]): IO[Unit] = args match {
    case annotationProjectId +: taskSizeMeters +: Nil =>
      val xa = RFTransactor.nonHikariTransactor(RFTransactor.TransactorConfig())
      new CreateTaskGrid(
        UUID.fromString(annotationProjectId),
        taskSizeMeters.toDouble,
        xa
      ).run()
    case _ =>
      IO.raiseError(
        new Exception("Must provide exactly one annotation project id")
      )
  }
}
