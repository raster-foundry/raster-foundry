package com.rasterfoundry.batch.groundwork

import com.rasterfoundry.batch.Job
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.util.RFTransactor
import com.rasterfoundry.database.{
  AnnotationProjectDao,
  ProjectDao,
  TaskDao,
  UserDao
}
import com.rasterfoundry.datamodel.{
  AnnotationProjectStatus,
  Task,
  TaskStatus,
  TaskType
}
import com.rasterfoundry.notification.intercom.Model._
import com.rasterfoundry.notification.intercom.{
  IntercomConversation,
  IntercomNotifier,
  LiveIntercomNotifier
}

import cats.data.OptionT
import cats.effect.{Async, IO, LiftIO}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend

import java.util.UUID

class CreateTaskGrid(
    annotationProjectId: UUID,
    taskSizeMeters: Double,
    notifier: IntercomNotifier[IO],
    xa: Transactor[IO]
) extends LazyLogging {

  private def info(s: String): ConnectionIO[Unit] =
    LiftIO[ConnectionIO].liftIO(
      IO { logger.info(s) }
    )

  val dbIO = new DbIO(xa);

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
        annotationProject.id,
        None,
        Some(TaskType.Label),
        None,
        None
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
              taskSizeMeters = Some(taskSizeMeters),
              status = AnnotationProjectStatus.Ready
            ),
            annotationProject.id
          )
      }
      _ <- OptionT.liftF { info("Updated annotation project") }
    } yield annotationProject).value.transact(xa) flatMap {
      case Some(annotationProject) =>
        val message = Message(annotationProject.campaignId match {
          case Some(campaignId) =>
            s"""Your image "${annotationProject.name}" is ready! ${Config.groundworkUrlBase}/app/campaign/${campaignId}/overview?s=${annotationProject.id}"""
          case _ =>
            s"""Your project "${annotationProject.name}" is ready! ${Config.groundworkUrlBase}/app/projects/${annotationProject.id}/overview"""
        })

        IntercomConversation.notifyIO(
          annotationProject.createdBy,
          message,
          dbIO.groundworkConfig,
          notifier,
          dbIO.getConversation,
          dbIO.insertConversation
        )

      case None =>
        (for {
          projectO <-
            AnnotationProjectDao.query
              .filter(annotationProjectId)
              .selectOption
          _ <- projectO traverse { project =>
            AnnotationProjectDao.update(
              project.copy(status = AnnotationProjectStatus.TaskGridFailure),
              project.id
            )
          }
          ownerO <- projectO traverse { project =>
            UserDao.unsafeGetUserById(project.createdBy)
          }
          _ <- (ownerO, projectO).tupled traverse {
            case (user, project) =>
              val entity =
                if (project.campaignId.isEmpty) "project" else "image"
              val message = Message(
                s"""
                  | Your ${entity} "${project.name}" failed to process. If you'd like help
                  | troubleshooting, please reach out to us here or at
                  | groundwork@azavea.com."
                  """.trim.stripMargin
              )
              LiftIO[ConnectionIO].liftIO {
                IntercomConversation.notifyIO(
                  user.id,
                  message,
                  dbIO.groundworkConfig,
                  notifier,
                  dbIO.getConversation,
                  dbIO.insertConversation
                )
              }
          }
        } yield ()).transact(xa)
    }
}

object CreateTaskGrid extends Job {

  val name = "create-task-grid"

  val getBackend = for {
    backendRef <- Async.memoize {
      AsyncHttpClientCatsBackend[IO]()
    }
    backend <- backendRef
  } yield backend

  def runJob(args: List[String]): IO[Unit] =
    args match {
      case annotationProjectId +: taskSizeMeters +: Nil =>
        val xa =
          RFTransactor.nonHikariTransactor(RFTransactor.TransactorConfig())
        for {
          backend <- getBackend
          _ <- new CreateTaskGrid(
            UUID.fromString(annotationProjectId),
            taskSizeMeters.toDouble,
            new LiveIntercomNotifier[IO](backend),
            xa
          ).run()
        } yield ()
      case _ =>
        IO.raiseError(
          new Exception("Must provide exactly one annotation project id")
        )
    }
}
