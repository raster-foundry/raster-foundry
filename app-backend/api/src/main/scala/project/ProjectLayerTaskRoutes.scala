package com.rasterfoundry.api.project

import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.datamodel._
import com.rasterfoundry.database._
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.akkautil._

import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import cats.effect._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie.{ConnectionIO, Transactor}
import doobie.implicits._

import scala.concurrent.ExecutionContext.Implicits.global

import java.util.UUID

trait ProjectLayerTaskRoutes
    extends Authentication
    with CommonHandlers
    with PaginationDirectives
    with ProjectAuthorizationDirectives
    with QueryParametersCommon {

  implicit val xa: Transactor[IO]

  def listLayerTasks(projectId: UUID, layerId: UUID): Route = authenticate {
    user =>
      {
        authorizeAsync {
          ProjectDao
            .authProjectLayerExist(
              projectId,
              layerId,
              user,
              ActionType.Annotate
            )
            .transact(xa)
            .unsafeToFuture
        } {
          (withPagination & taskQueryParameters) { (page, taskParams) =>
            complete {
              TaskDao
                .listTasks(
                  taskParams,
                  projectId,
                  layerId,
                  page
                )
                .transact(xa)
                .unsafeToFuture
            }
          }
        }
      }
  }

  def createLayerTask(projectId: UUID, layerId: UUID): Route = authenticate {
    user =>
      {
        authorizeAsync {
          ProjectDao
            .authProjectLayerExist(projectId, layerId, user, ActionType.Edit)
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[Task.TaskFeatureCollectionCreate]) { tfc =>
            complete(
              StatusCodes.Created,
              TaskDao
                .insertTasks(tfc, user)
                .transact(xa)
                .unsafeToFuture
            )
          }
        }
      }
  }

  def createLayerTaskGrid(projectId: UUID, layerId: UUID): Route =
    authenticate { user =>
      {
        authorizeAsync {
          ProjectDao
            .authProjectLayerExist(projectId, layerId, user, ActionType.Edit)
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[Task.TaskGridFeatureCreate]) { tgf =>
            complete(
              StatusCodes.Created,
              TaskDao
                .insertTasksByGrid(
                  Task.TaskPropertiesCreate(
                    projectId,
                    layerId,
                    TaskStatus.Unlabeled
                  ),
                  tgf,
                  user
                )
                .transact(xa)
                .unsafeToFuture
            )
          }
        }
      }
    }

  def deleteLayerTasks(projectId: UUID, layerId: UUID): Route =
    authenticate { user =>
      {
        authorizeAsync {
          ProjectDao
            .authProjectLayerExist(
              projectId,
              layerId,
              user,
              ActionType.Edit
            )
            .transact(xa)
            .unsafeToFuture
        } {
          complete {
            TaskDao
              .deleteLayerTasks(
                projectId,
                layerId
              )
              .transact(xa)
              .unsafeToFuture map { _ =>
              HttpResponse(StatusCodes.NoContent)
            }
          }
        }
      }
    }

  def getTask(projectId: UUID, layerId: UUID, taskId: UUID): Route =
    authenticate { user =>
      {
        authorizeAsync {
          ProjectDao
            .authProjectLayerExist(
              projectId,
              layerId,
              user,
              ActionType.Annotate
            )
            .transact(xa)
            .unsafeToFuture
        } {
          complete {
            TaskDao.getTaskWithActions(taskId).transact(xa).unsafeToFuture
          }
        }
      }
    }

  def updateTask(projectId: UUID, layerId: UUID, taskId: UUID): Route =
    authenticate { user =>
      (authorizeAsync {
        ProjectDao
          .authProjectLayerExist(
            projectId,
            layerId,
            user,
            ActionType.Annotate
          )
          .transact(xa)
          .unsafeToFuture
      } & authorizeAsync {
        TaskDao
          .isLockingUserOrUnlocked(taskId, user)
          .transact(xa)
          .unsafeToFuture
      }) {
        entity(as[Task.TaskFeatureCreate]) { tfc =>
          complete {
            TaskDao.updateTask(taskId, tfc, user).transact(xa) map {
              case None =>
                HttpResponse(StatusCodes.NotFound)
              case _ =>
                HttpResponse(StatusCodes.NoContent)
            } unsafeToFuture
          }
        }
      }
    }

  private def toggleLock(
      projectId: UUID,
      layerId: UUID,
      taskId: UUID,
      f: (User => ConnectionIO[Option[Task.TaskFeature]])
  ): Route =
    authenticate { user =>
      (authorizeAsync {
        ProjectDao
          .authProjectLayerExist(
            projectId,
            layerId,
            user,
            ActionType.Annotate
          )
          .transact(xa)
          .unsafeToFuture
      } & authorizeAsync {
        TaskDao
          .isLockingUserOrUnlocked(taskId, user)
          .transact(xa)
          .unsafeToFuture
      }) {
        complete {
          f(user).transact(xa).unsafeToFuture
        }
      }
    }

  def lockTask(projectId: UUID, layerId: UUID, taskId: UUID): Route =
    toggleLock(projectId, layerId, taskId, TaskDao.lockTask(taskId))

  def unlockTask(projectId: UUID, layerId: UUID, taskId: UUID): Route =
    toggleLock(projectId, layerId, taskId, _ => TaskDao.unlockTask(taskId))

  def deleteTask(projectId: UUID, layerId: UUID, taskId: UUID): Route =
    authenticate { user =>
      {
        authorizeAsync {
          ProjectDao
            .authProjectLayerExist(projectId, layerId, user, ActionType.Edit)
            .transact(xa)
            .unsafeToFuture
        } {
          complete {
            TaskDao.query.filter(taskId).delete.transact(xa).unsafeToFuture
          }
        }
      }
    }

  def getTaskUserSummary(projectId: UUID, layerId: UUID): Route = authenticate {
    user =>
      {
        authorizeAsync {
          ProjectDao
            .authProjectLayerExist(
              projectId,
              layerId,
              user,
              ActionType.Annotate
            )
            .transact(xa)
            .unsafeToFuture
        } {
          complete {
            TaskDao
              .getTaskUserSummary(projectId, layerId)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
  }
}
