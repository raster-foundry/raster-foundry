package com.rasterfoundry.api.annotationProject

import com.rasterfoundry.akkautil._
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._
import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.common.RollbarNotifier

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import cats.effect.IO
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie.util.transactor.Transactor
import doobie._
import doobie.implicits._

import java.util.UUID

trait AnnotationProjectRoutes
    extends CommonHandlers
    with Directives
    with Authentication
    with PaginationDirectives
    with QueryParametersCommon
    with AnnotationProjectTaskRoutes
    with AnnotationProjectPermissionRoutes
    with RollbarNotifier {

  val xa: Transactor[IO]

  val annotationProjectRoutes: Route = {
    pathEndOrSingleSlash {
      get {
        listAnnotationProjects
      } ~
        post {
          createAnnotationProject
        }
    } ~
      pathPrefix(JavaUUID) { projectId =>
        pathEndOrSingleSlash {
          get {
            getAnnotationProject(projectId)
          } ~
            put {
              updateAnnotationProject(projectId)
            } ~
            delete {
              deleteAnnotationProject(projectId)
            }
        } ~
          pathPrefix("permissions") {
            pathEndOrSingleSlash {
              get {
                listPermissions(projectId)
              } ~
                put {
                  replacePermissions(projectId)
                } ~
                post {
                  addPermission(projectId)
                } ~
                delete {
                  deletePermissions(projectId)
                }
            }
          } ~
          pathPrefix("tasks") {
            pathEndOrSingleSlash {
              get {
                listTasks(projectId)
              } ~ post {
                createTask(projectId)
              } ~ delete {
                deleteTasks(projectId)
              }
            } ~
              pathPrefix("grid") {
                post {
                  createTaskGrid(projectId)
                }
              } ~
              pathPrefix("user-stats") {
                get {
                  getTaskUserStats(projectId)
                }
              } ~
              pathPrefix(JavaUUID) { taskId =>
                pathEndOrSingleSlash {
                  get {
                    getTask(projectId, taskId)
                  } ~ put {
                    updateTask(projectId, taskId)
                  } ~ delete {
                    deleteTask(projectId, taskId)
                  }
                } ~ pathPrefix("lock") {
                  pathEndOrSingleSlash {
                    post {
                      lockTask(projectId, taskId)
                    } ~ delete {
                      unlockTask(projectId, taskId)
                    }
                  }
                }
              }
          }
      }
  }

  def listAnnotationProjects: Route = ???

  def createAnnotationProject: Route = authenticate { user =>
    authorizeScopeLimit(
      AnnotationProjectDao.countUserProjects(user).transact(xa).unsafeToFuture,
      Domain.Projects,
      Action.Create,
      user
    ) {
      entity(as[AnnotationProject.Create]) { newAnnotationProject =>
        onSuccess(
          AnnotationProjectDao
            .insert(newAnnotationProject, user)
            .transact(xa)
            .unsafeToFuture
        ) { annotationProject =>
          complete((StatusCodes.Created, annotationProject))
        }
      }
    }
  }

  def getAnnotationProject(projectId: UUID): Route = authenticate { user =>
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
        rejectEmptyResponse {
          complete {
            AnnotationProjectDao
              .getWithRelatedById(projectId)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }
  }

  def updateAnnotationProject(projectId: UUID): Route = ???

  def deleteAnnotationProject(projectId: UUID): Route = authenticate { user =>
    authorizeScope(
      ScopedAction(Domain.AnnotationProjects, Action.Delete, None),
      user
    ) {
      authorizeAuthResultAsync {
        AnnotationProjectDao
          .authorized(
            user,
            ObjectType.AnnotationProject,
            projectId,
            ActionType.Delete
          )
          .transact(xa)
          .unsafeToFuture
      } {
        onSuccess(
          AnnotationProjectDao
            .deleteById(projectId)
            .transact(xa)
            .unsafeToFuture
        ) {
          completeSingleOrNotFound
        }
      }
    }
  }

}
