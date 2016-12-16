package com.azavea.rf.project

import java.util.UUID

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling._
import akka.http.scaladsl.model.StatusCodes

import com.lonelyplanet.akka.http.extensions.PaginationDirectives

import com.azavea.rf.auth.Authentication
import com.azavea.rf.database.tables._
import com.azavea.rf.database.Database
import com.azavea.rf.datamodel._
import com.azavea.rf.scene._
import com.azavea.rf.utils.queryparams.QueryParametersCommon
import com.azavea.rf.utils.UserErrorHandler


trait ProjectRoutes extends Authentication
    with QueryParametersCommon
    with SceneQueryParameterDirective
    with PaginationDirectives
    with UserErrorHandler {

  implicit def database: Database

  implicit val rawIntFromEntityUnmarshaller: FromEntityUnmarshaller[UUID] =
    PredefinedFromEntityUnmarshallers.stringUnmarshaller.map{ s =>
      UUID.fromString(s)
    }

  val BULK_OPERATION_MAX_LIMIT = 100

  val projectRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listProjects } ~
      post { createProject }
    } ~
    pathPrefix(JavaUUID) { projectId =>
      pathEndOrSingleSlash {
        get { getProject(projectId) } ~
        put { updateProject(projectId) } ~
        delete { deleteProject(projectId) }
      } ~
      pathPrefix("scenes") {
        pathEndOrSingleSlash {
          get { listProjectScenes(projectId) } ~
          post { addProjectScenes(projectId) } ~
          put { updateProjectScenes(projectId) } ~
          delete { deleteProjectScenes(projectId) }
        } ~
        pathPrefix("ordered") {
          pathEndOrSingleSlash {
            post { setProjectScenesOrder(projectId) } ~
            get { listProjectScenesOrdered(projectId) }
          }
        }
      }
    }
  }

  def listProjects: Route = authenticate { user =>
    (withPagination & projectQueryParameters) { (page, projectQueryParameters) =>
      complete {
        Projects.listProjects(page, projectQueryParameters, user)
      }
    }
  }

  def createProject: Route = authenticate { user =>
    entity(as[Project.Create]) { newProject =>
      onSuccess(Projects.insertProject(newProject.toProject(user.id))) { project =>
        complete(StatusCodes.Created, project)
      }
    }
  }

  def getProject(projectId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        Projects.getProject(projectId)
      }
    }
  }

  def updateProject(projectId: UUID): Route = authenticate { user =>
    entity(as[Project]) { updatedProject =>
      onSuccess(Projects.updateProject(updatedProject, projectId, user)) {
        case 1 => complete(StatusCodes.NoContent)
        case count => throw new IllegalStateException(
          s"Error updating project: update result expected to be 1, was $count"
        )
      }
    }
  }

  def deleteProject(projectId: UUID): Route = authenticate { user =>
    onSuccess(Projects.deleteProject(projectId)) {
      case 1 => complete(StatusCodes.NoContent)
      case 0 => complete(StatusCodes.NotFound)
      case count => throw new IllegalStateException(
        s"Error deleting project: delete result expected to be 1, was $count"
      )
    }
  }

  def listProjectScenes(projectId: UUID): Route = authenticate { user =>
    (withPagination & sceneQueryParameters) { (page, sceneParams) =>
      complete {
        Projects.listProjectScenes(projectId, page, sceneParams, user)
      }
    }
  }

  def listProjectScenesOrdered(projectId: UUID): Route = authenticate { user =>
    (withPagination & sceneQueryParameters) { (page, sceneParams) =>
      complete {
        ScenesToProjects.listS2POrder(projectId)
      }
    }
  }

  def setProjectScenesOrder(projectId: UUID): Route = authenticate { user =>
    entity(as[Seq[UUID]]) { sceneIds =>
      if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
        complete(StatusCodes.RequestEntityTooLarge)
      }

      complete {
        ScenesToProjects.setS2POrder(projectId, sceneIds)
      }
    }
  }

  def addProjectScenes(projectId: UUID): Route = authenticate { user =>
    entity(as[Seq[UUID]]) { sceneIds =>
      if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
        complete(StatusCodes.RequestEntityTooLarge)
      }

      complete {
        Projects.addScenesToProject(sceneIds, projectId)
      }
    }
  }

  def updateProjectScenes(projectId: UUID): Route = authenticate { user =>
    entity(as[Seq[UUID]]) { sceneIds =>
      if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
        complete(StatusCodes.RequestEntityTooLarge)
      }

      complete {
        Projects.replaceScenesInProject(sceneIds, projectId)
      }
    }
  }

  def deleteProjectScenes(projectId: UUID): Route = authenticate { user =>
    entity(as[Seq[UUID]]) { sceneIds =>
      if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
        complete(StatusCodes.RequestEntityTooLarge)
      }

      onSuccess(Projects.deleteScenesFromProject(sceneIds, projectId)) {
        _ => complete(StatusCodes.NoContent)
      }
    }
  }
}
