package com.rasterfoundry.batch.groundwork

import com.rasterfoundry.batch.Job
import com.rasterfoundry.database.{
  AnnotationProjectDao,
  ProjectDao,
  TaskDao,
  UserDao
}
import com.rasterfoundry.datamodel.{Task, TaskStatus}
import com.rasterfoundry.database.util.RFTransactor

import cats.data.OptionT
import cats.effect.IO
import cats.implicits._
import doobie.implicits._
import doobie.Transactor

import java.util.UUID

class CreateTaskGrid(annotationProjectId: UUID, xa: Transactor[IO]) {
  // assuming upload has been processed, there is:
  //
  // - a scene
  // - in a layer
  // - in a project
  // - for this annotation project
  //
  // we want to:
  //
  // - [x] get its footprint
  // - [x] create a TaskGridFeatureCreate with the footprint
  // - also with the sizeMeters that corresponds to the taskSizePixels of this annotation project
  // - [x] use the existing TaskDao method to insert the task grid
  // - [x] update annotation project aoi with the footprint
  // - [x] mark the annotation project ready

  val taskSizeMeters: Int = ??? // TODO

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
        Task.TaskGridCreateProperties(taskSizeMeters) footprint
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
    case annotationProjectId +: Nil =>
      val xa = RFTransactor.nonHikariTransactor(RFTransactor.TransactorConfig())
      new CreateTaskGrid(UUID.fromString(annotationProjectId), xa).run()
    case _ =>
      IO.raiseError(
        new Exception("Must provide exactly one annotation project id")
      )
  }
}
