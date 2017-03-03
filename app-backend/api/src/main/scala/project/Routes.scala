package com.azavea.rf.api.project

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling._
import akka.http.scaladsl.model.StatusCodes
import spray.json._
import DefaultJsonProtocol._
import com.lonelyplanet.akka.http.extensions.{PaginationDirectives, Order}

import com.azavea.rf.common.{Authentication, UserErrorHandler}
import com.azavea.rf.database.tables._
import com.azavea.rf.database.query._
import com.azavea.rf.database.Database
import com.azavea.rf.datamodel._
import com.azavea.rf.api.scene._
import com.azavea.rf.api.utils.queryparams.QueryParametersCommon

import java.util.UUID

trait ProjectRoutes extends Authentication
    with QueryParametersCommon
    with QueryParameterJsonFormat
    with SceneQueryParameterDirective
    with PaginationDirectives
    with UserErrorHandler {

  implicit def database: Database

  implicit val rawIntFromEntityUnmarshaller: FromEntityUnmarshaller[UUID] =
    PredefinedFromEntityUnmarshallers.stringUnmarshaller.map{ s =>
      UUID.fromString(s)
    }

  /** This seems like it almost definitely shouldn't be necessary, but scala refuses
    * to use the jsonFormats implicitly available from QueryParameterJsonFormat,
    * and this implicit makes it stop complaining.
    *
    * Writing unmarshallers seems more manual and superfluous than writing json formats,
    * but here we are.
    */
  implicit val combinedSceneQueryParamsUnmarshaller: FromEntityUnmarshaller[CombinedSceneQueryParams] =
    PredefinedFromEntityUnmarshallers.stringUnmarshaller.map{ s =>
      CombinedSceneQueryParamsReader.read(s.parseJson)
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
          pathPrefix("bulk-add-from-query") {
            pathEndOrSingleSlash {
              post { addProjectScenesFromQueryParams(projectId) }
            }
          }
      } ~
      pathPrefix("mosaic") {
        pathEndOrSingleSlash {
          get { getProjectMosaicDefinition(projectId) }
        } ~
        pathPrefix(JavaUUID) { sceneId =>
          get { getProjectSceneColorCorrectParams(projectId, sceneId) } ~
          post { setProjectSceneColorCorrectParams(projectId, sceneId) }
        }
      } ~
      pathPrefix("order") {
        pathEndOrSingleSlash {
          get { listProjectSceneOrder(projectId) } ~
          post { setProjectSceneOrder(projectId) }
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

  /** List a project's scenes according to their manually defined ordering */
  def listProjectSceneOrder(projectId: UUID): Route = authenticate { user =>
    withPagination { page =>
      complete {
        Projects.listProjectSceneOrder(projectId, page, user)
      }
    }
  }

  /** Set the manually defined z-ordering for scenes within a given project */
  def setProjectSceneOrder(projectId: UUID): Route = authenticate { user =>
    entity(as[Seq[UUID]]) { sceneIds =>
      if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
        complete(StatusCodes.RequestEntityTooLarge)
      }

      complete {
        ScenesToProjects.setManualOrder(projectId, sceneIds)
      }
    }
  }

  /** Get the color correction paramters for a project/scene pairing */
  def getProjectSceneColorCorrectParams(projectId: UUID, sceneId: UUID) = authenticate { user =>
    complete {
      ScenesToProjects.getColorCorrectParams(projectId, sceneId)
    }
  }

  /** Set color correction parameters for a project/scene pairing */
  def setProjectSceneColorCorrectParams(projectId: UUID, sceneId: UUID) = authenticate { user =>
    entity(as[ColorCorrect.Params]) { ccParams =>
      onSuccess(ScenesToProjects.setColorCorrectParams(projectId, sceneId, ccParams)) {
        case 1 => complete(StatusCodes.NoContent)
        case count => throw new IllegalStateException(
          s"Error updating scene's color correction: update result expected to be 1, was $count"
        )
      }
    }
  }

  /** Get the information which defines mosaicing behavior for each scene in a given project */
  def getProjectMosaicDefinition(projectId: UUID) = authenticate { user =>
    rejectEmptyResponse {
      complete {
        ScenesToProjects.getMosaicDefinition(projectId)
      }
    }
  }

  def addProjectScenes(projectId: UUID): Route = authenticate { user =>
    entity(as[Seq[UUID]]) { sceneIds =>
      if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
        complete(StatusCodes.RequestEntityTooLarge)
      }

      complete {
        Projects.addScenesToProject(sceneIds, projectId, user)
      }
    }
  }

  def addProjectScenesFromQueryParams(projectId: UUID): Route = authenticate { user =>
    entity(as[CombinedSceneQueryParams]) { combinedSceneQueryParams =>
      onSuccess(Projects.addScenesToProjectFromQuery(combinedSceneQueryParams, projectId, user)) {
        scenesAdded => complete((StatusCodes.Created, scenesAdded))
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
