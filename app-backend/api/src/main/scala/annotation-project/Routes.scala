package com.rasterfoundry.api.annotationProject

import com.rasterfoundry.akkautil.{Authentication, CommonHandlers}
import com.rasterfoundry.database.AnnotationProjectDao
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import cats.effect.IO
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie.util.transactor.Transactor
import doobie._
import doobie.implicits._

import java.util.UUID

trait AnnotationProjectRoutes extends CommonHandlers with Authentication {

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
            .insertAnnotationProject(newAnnotationProject, user)
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
              .getAnnotationProjectWithRelatedById(projectId)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }
  }

  def updateAnnotationProject(projectId: UUID): Route = ???

  def deleteAnnotationProject(projectId: UUID): Route = ???

  def listTasks(projectId: UUID): Route = ???

  def createTask(projectId: UUID): Route = ???

  def deleteTasks(projectId: UUID): Route = ???

  def createTaskGrid(projectId: UUID): Route = ???

  def getTaskUserStats(projectId: UUID): Route = ???

  def getTask(projectId: UUID, taskId: UUID): Route = ???

  def updateTask(projectId: UUID, taskId: UUID): Route = ???

  def deleteTask(projectId: UUID, taskId: UUID): Route = ???

  def lockTask(projectId: UUID, taskId: UUID): Route = ???

  def unlockTask(projectId: UUID, taskId: UUID): Route = ???
}
