package com.rasterfoundry.api.annotationProject

import com.rasterfoundry.akkautil._
import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.database._
import com.rasterfoundry.database.filter.Filterables._
import com.rasterfoundry.datamodel.GeoJsonCodec._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import cats.effect.Blocker
import cats.effect.IO
import com.google.common.util.concurrent.ThreadFactoryBuilder
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor

import scala.concurrent.ExecutionContext

import java.util.UUID
import java.util.concurrent.Executors

trait AnnotationProjectTaskRoutes
    extends CommonHandlers
    with Directives
    with Authentication
    with PaginationDirectives
    with QueryParametersCommon {

  val taskGridContext = ExecutionContext.fromExecutor(
    Executors.newCachedThreadPool(
      new ThreadFactoryBuilder().setNameFormat("task-grid-%d").build()
    )
  )

  val taskGridContextShift = IO.contextShift(taskGridContext)
  val taskGridBlocker = Blocker.liftExecutionContext(taskGridContext)

  val xa: Transactor[IO]

  def listAnnotationProjectTasks(projectId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.ReadTasks, None),
        user
      ) {
        authorizeAuthResultAsync {
          AnnotationProjectDao
            .authorized(
              user,
              ObjectType.AnnotationProject,
              projectId,
              ActionType.Annotate
            )
            .transact(xa)
            .unsafeToFuture
        } {
          (withPagination & taskQueryParameters) { (page, taskParams) =>
            complete {
              (
                TaskDao
                  .listTasks(
                    taskParams,
                    projectId,
                    page
                  )
                )
                .transact(xa)
                .unsafeToFuture
            }
          }
        }
      }
    }

  def createTasks(projectId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.CreateTasks, None),
        user
      ) {
        authorizeAuthResultAsync {
          AnnotationProjectDao
            .authorized(
              user,
              ObjectType.AnnotationProject,
              projectId,
              ActionType.Edit
            )
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

  def deleteTasks(projectId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.DeleteTasks, None),
        user
      ) {
        authorizeAuthResultAsync {
          AnnotationProjectDao
            .authorized(
              user,
              ObjectType.AnnotationProject,
              projectId,
              ActionType.Edit
            )
            .transact(xa)
            .unsafeToFuture
        } {
          complete {
            TaskDao
              .deleteProjectTasks(projectId)
              .transact(xa)
              .unsafeToFuture map { _ =>
              HttpResponse(StatusCodes.NoContent)
            }
          }
        }
      }
    }

  def createTaskGrid(projectId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.CreateTaskGrid, None),
        user
      ) {
        authorizeAuthResultAsync {
          AnnotationProjectDao
            .authorized(
              user,
              ObjectType.AnnotationProject,
              projectId,
              ActionType.Edit
            )
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[Task.TaskGridFeatureCreate]) { tgf =>
            complete(
              StatusCodes.Accepted,
              taskGridBlocker
                .blockOn(
                  TaskDao
                    .insertTasksByGrid(
                      Task
                        .TaskPropertiesCreate(
                          TaskStatus.Unlabeled,
                          projectId,
                          None,
                          None,
                          None,
                          None
                        ),
                      tgf,
                      user
                    )
                    .transact(xa)
                    .start(taskGridContextShift)
                )(taskGridContextShift)
                .void
                .unsafeToFuture
            )
          }
        }
      }
    }

  def getTaskUserSummary(projectId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.ReadTasks, None),
        user
      ) {
        {
          authorizeAuthResultAsync {
            AnnotationProjectDao
              .authorized(
                user,
                ObjectType.AnnotationProject,
                projectId,
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

  def getTask(projectId: UUID, taskId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.ReadTasks, None),
        user
      ) {
        {
          authorizeAuthResultAsync {
            AnnotationProjectDao
              .authorized(
                user,
                ObjectType.AnnotationProject,
                projectId,
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

  def updateTask(projectId: UUID, taskId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.UpdateTasks, None),
        user
      ) {
        authorizeAsync {
          (for {
            auth1 <- AnnotationProjectDao
              .authorized(
                user,
                ObjectType.AnnotationProject,
                projectId,
                ActionType.Annotate
              )
            auth2 <- TaskDao.isLockingUserOrUnlocked(taskId, user)
          } yield {
            auth1.toBoolean && auth2
          }).transact(xa).unsafeToFuture
        } {
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

  def deleteTask(projectId: UUID, taskId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.DeleteTasks, None),
        user
      ) {
        authorizeAuthResultAsync {
          AnnotationProjectDao
            .authorized(
              user,
              ObjectType.AnnotationProject,
              projectId,
              ActionType.Edit
            )
            .transact(xa)
            .unsafeToFuture
        } {
          complete {
            TaskDao.query.filter(taskId).delete.transact(xa).unsafeToFuture
          }
        }
      }
    }

  def lockTask(projectId: UUID, taskId: UUID): Route =
    toggleLock(projectId, TaskDao.lockTask(taskId))

  def unlockTask(projectId: UUID, taskId: UUID): Route =
    toggleLock(projectId, _ => TaskDao.unlockTask(taskId))

  private def toggleLock(
      projectId: UUID,
      f: (User => ConnectionIO[Option[Task.TaskFeature]])
  ): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.CreateAnnotation, None),
        user
      ) {
        authorizeAsync {
          AnnotationProjectDao
            .authorized(
              user,
              ObjectType.AnnotationProject,
              projectId,
              ActionType.Annotate
            )
            .transact(xa)
            .map(_.toBoolean)
            .unsafeToFuture
        } {
          complete {
            f(user).transact(xa).unsafeToFuture
          }
        }

      }
    }

  def listTaskLabels(projectId: UUID, taskId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.Read, None),
        user
      ) {
        authorizeAuthResultAsync {
          AnnotationProjectDao
            .authorized(
              user,
              ObjectType.AnnotationProject,
              projectId,
              ActionType.View
            )
            .transact(xa)
            .unsafeToFuture
        } {
          complete {
            AnnotationLabelDao
              .listWithClassesByProjectIdAndTaskId(
                projectId,
                taskId
              )
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }

  def addTaskLabels(projectId: UUID, taskId: UUID): Route =
    addLabels(
      projectId,
      taskId,
      ActionType.Annotate,
      List(
        TaskStatus.Unlabeled,
        TaskStatus.LabelingInProgress,
        TaskStatus.Labeled
      )
    )

  def validateTaskLabels(projectId: UUID, taskId: UUID): Route =
    addLabels(
      projectId,
      taskId,
      ActionType.Validate,
      List(
        TaskStatus.Labeled,
        TaskStatus.ValidationInProgress,
        TaskStatus.Validated
      )
    )

  private def addLabels(
      projectId: UUID,
      taskId: UUID,
      actionType: ActionType,
      requiredStatuses: List[TaskStatus]
  ): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.CreateAnnotation, None),
        user
      ) {
        authorizeAsync {
          (for {
            auth1 <- AnnotationProjectDao
              .authorized(
                user,
                ObjectType.AnnotationProject,
                projectId,
                actionType
              )
            auth2 <- TaskDao.hasStatus(
              taskId,
              requiredStatuses
            )
          } yield {
            auth1.toBoolean && auth2
          }).transact(xa).unsafeToFuture
        } {
          entity(as[AnnotationLabelWithClassesFeatureCollectionCreate]) { fc =>
            val annotationLabelWithClassesCreate = fc.features map {
              _.toAnnotationLabelWithClassesCreate
            }
            onSuccess(
              (for {
                _ <- AnnotationLabelDao
                  .deleteByProjectIdAndTaskId(projectId, taskId)
                insert <- AnnotationLabelDao
                  .insertAnnotations(
                    projectId,
                    taskId,
                    annotationLabelWithClassesCreate.toList,
                    user
                  )
              } yield {
                insert
              }).transact(xa)
                .unsafeToFuture
                .map { annotations: List[AnnotationLabelWithClasses] =>
                  fromSeqToFeatureCollection[
                    AnnotationLabelWithClasses,
                    AnnotationLabelWithClasses.GeoJSON
                  ](
                    annotations
                  )
                }
            ) { createdAnnotation =>
              complete((StatusCodes.Created, createdAnnotation))
            }
          }
        }
      }
    }

  def deleteTaskLabels(projectId: UUID, task: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.DeleteAnnotation, None),
        user
      ) {
        authorizeAuthResultAsync {
          AnnotationProjectDao
            .authorized(
              user,
              ObjectType.AnnotationProject,
              projectId,
              ActionType.Annotate
            )
            .transact(xa)
            .unsafeToFuture
        } {
          complete {
            AnnotationLabelDao
              .deleteByProjectIdAndTaskId(projectId, task)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }

  def children(projectId: UUID, taskId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.ReadTasks, None),
        user
      ) {
        {
          authorizeAuthResultAsync {
            AnnotationProjectDao
              .authorized(
                user,
                ObjectType.AnnotationProject,
                projectId,
                ActionType.Annotate
              )
              .transact(xa)
              .unsafeToFuture
          } {
            withPagination { page =>
              complete {
                TaskDao.children(taskId, page).transact(xa).unsafeToFuture
              }
            }
          }
        }
      }
    }

  def splitTask(projectId: UUID, taskId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.ReadTasks, None),
        user
      ) {
        authorizeAuthResultAsync {
          AnnotationProjectDao
            .authorized(
              user,
              ObjectType.AnnotationProject,
              projectId,
              ActionType.Annotate
            )
            .transact(xa)
            .unsafeToFuture
        } {
          complete {
            TaskDao.splitTask(taskId, user).transact(xa).unsafeToFuture
          }
        }
      }
    }
}
