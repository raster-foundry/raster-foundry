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
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}

import java.util.UUID

class CreateTaskGrid(
    annotationProjectId: UUID,
    taskSizeMeters: Double,
    xa: Transactor[IO]
) extends LazyLogging {

  private def info(s: String): ConnectionIO[Unit] =
    LiftIO[ConnectionIO].liftIO(
      IO { logger.info(s) }
    )

  def run(): IO[Unit] =
    (for {
      annotationProject <- OptionT {
        AnnotationProjectDao.getProjectById(annotationProjectId)
      }
      _ <- OptionT.liftF {
        info(s"Got annotation project ${annotationProject.name}")
      }
      owner <- OptionT {
        UserDao.getUserById(annotationProject.createdBy)
      }
      footprint <- OptionT {
        annotationProject.projectId traverse { projectId =>
          ProjectDao.getFootprint(projectId)
        }
      } <* OptionT.liftF {
        info("Got annotation project footprint")
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
      _ <- OptionT.liftF { info("Inserted tasks") }
      // even though the `aoi` and `taskSizeMeters` are updated when inserting tasks,
      // the `annotationProject` here does not know that because it happened before
      // task insert. this is why we update these fields in the following lines
      _ <- OptionT.liftF {
        AnnotationProjectDao
          .update(
            annotationProject.copy(
              aoi = footprint,
              taskSizeMeters = Some(taskSizeMeters)
            ),
            annotationProject.id
          )
      }
      _ <- OptionT.liftF { info("Updated annotation project") }
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
