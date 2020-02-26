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
import cats.effect.IO
import cats.implicits._
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class CreateTaskGrid(
    annotationProjectId: UUID,
    taskSizeMeters: Double,
    xa: Transactor[IO]
) {

  def run(): IO[Unit] =
    (for {
      annotationProject <- OptionT {
        AnnotationProjectDao.getProjectById(annotationProjectId).transact(xa)
      }
      owner <- OptionT {
        UserDao.getUserById(annotationProject.createdBy).transact(xa)
      }
      footprint <- OptionT {
        annotationProject.projectId traverse { projectId =>
          ProjectDao.getFootprint(projectId).transact(xa)
        }
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
          .transact(xa)
      }
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
          .transact(xa)
      }
    } yield ()).value.void
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
