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

import scala.concurrent.ExecutionContext
import java.util.UUID

trait ProjectLayerTaskRoutes
    extends Authentication
    with CommonHandlers
    with PaginationDirectives
    with ProjectAuthorizationDirectives
    with QueryParametersCommon {

  implicit val xa: Transactor[IO]
  implicit val ec: ExecutionContext

  def listLayerTasks(projectId: UUID, layerId: UUID): Route = authenticate {
    user =>
      authorizeScope(
        ScopedAction(Domain.Projects, Action.ReadTasks, None),
        user
      ) {
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
                (
                  taskParams.format match {
                    case Some(format) if format.toUpperCase == "SUMMARY" =>
                      TaskDao.listTaskGeomByStatus(
                        user,
                        projectId,
                        layerId,
                        taskParams.status
                      )
                    case _ =>
                      TaskDao
                        .listTasks(
                          taskParams,
                          projectId,
                          layerId,
                          page
                        )
                  }
                ).transact(xa).unsafeToFuture
              }
            }
          }
        }
      }
  }

  def createLayerTask(projectId: UUID, layerId: UUID): Route = authenticate {
    user =>
      authorizeScope(
        ScopedAction(Domain.Projects, Action.CreateTasks, None),
        user
      ) {
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
  }

  def createLayerTaskGrid(projectId: UUID, layerId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Projects, Action.CreateTaskGrid, None),
        user
      ) {
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
    }

  def deleteLayerTasks(projectId: UUID, layerId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Projects, Action.DeleteTasks, None),
        user
      ) {
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
    }

  def getTask(projectId: UUID, layerId: UUID, taskId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Projects, Action.ReadTasks, None),
        user
      ) {
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
    }

  def updateTask(projectId: UUID, layerId: UUID, taskId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Projects, Action.UpdateTasks, None),
        user
      ) {
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
    }

  private def toggleLock(
      projectId: UUID,
      layerId: UUID,
      taskId: UUID,
      f: (User => ConnectionIO[Option[Task.TaskFeature]])
  ): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Projects, Action.CreateAnnotation, None),
        user
      ) {
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
    }

  def lockTask(projectId: UUID, layerId: UUID, taskId: UUID): Route =
    toggleLock(projectId, layerId, taskId, TaskDao.lockTask(taskId))

  def unlockTask(projectId: UUID, layerId: UUID, taskId: UUID): Route =
    toggleLock(projectId, layerId, taskId, _ => TaskDao.unlockTask(taskId))

  def deleteTask(projectId: UUID, layerId: UUID, taskId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Projects, Action.DeleteTasks, None),
        user
      ) {
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
    }

  def getTaskUserSummary(projectId: UUID, layerId: UUID): Route = authenticate {
    user =>
      authorizeScope(
        ScopedAction(Domain.Projects, Action.ReadTasks, None),
        user
      ) {
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
            (userTaskActivityParameters) { userTaskActivityParams =>
              complete {
                TaskDao
                  .getTaskUserSummary(
                    projectId,
                    layerId,
                    userTaskActivityParams
                  )
                  .transact(xa)
                  .unsafeToFuture
              }
            }
          }
        }
      }
  }
}
