package com.azavea.rf.api.project

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling._
import akka.http.scaladsl.model.StatusCodes
import com.lonelyplanet.akka.http.extensions.{PaginationDirectives, Order}

import com.azavea.rf.common.{Authentication, UserErrorHandler, CommonHandlers}
import com.azavea.rf.database.tables._
import com.azavea.rf.database.query._
import com.azavea.rf.database.Database
import com.azavea.rf.datamodel._
import com.azavea.rf.api.scene._
import com.azavea.rf.api.utils.queryparams.QueryParametersCommon

import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.CirceSupport._

import java.util.UUID

trait ProjectRoutes extends Authentication
    with QueryParametersCommon
    with SceneQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler {

  implicit def database: Database

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
          put { setProjectSceneColorCorrectParams(projectId, sceneId) }
        } ~
        pathPrefix("bulk-update-color-corrections") {
          pathEndOrSingleSlash {
            post { setProjectScenesColorCorrectParams(projectId) }
          }
        }
      } ~
      pathPrefix("order") {
        pathEndOrSingleSlash {
          get { listProjectSceneOrder(projectId) } ~
          put { setProjectSceneOrder(projectId) }
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
      authorize(user.isInRootOrSameOrganizationAs(newProject)) {
        onSuccess(Projects.insertProject(newProject.toProject(user.id))) { project =>
          complete(StatusCodes.Created, project)
        }
      }
    }
  }

  def getProject(projectId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        Projects.getProject(projectId, user)
      }
    }
  }

  def updateProject(projectId: UUID): Route = authenticate { user =>
    entity(as[Project]) { updatedProject =>
      authorize(user.isInRootOrSameOrganizationAs(updatedProject)) {
        onSuccess(Projects.updateProject(updatedProject, projectId, user)) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteProject(projectId: UUID): Route = authenticate { user =>
    onSuccess(Projects.deleteProject(projectId, user)) {
      completeSingleOrNotFound
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

      onSuccess(ScenesToProjects.setManualOrder(projectId, sceneIds)) { updatedOrder =>
        complete(StatusCodes.NoContent)
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
      onSuccess(ScenesToProjects.setColorCorrectParams(projectId, sceneId, ccParams)) { sceneToProject =>
        complete(StatusCodes.NoContent)
      }
    }
  }

  /** Set color correction parameters for a list of scenes */
  def setProjectScenesColorCorrectParams(projectId: UUID) = authenticate { user =>
    entity(as[BatchParams]) { params =>
      onSuccess(ScenesToProjects.setColorCorrectParamsBatch(projectId, params)) { scenesToProject =>
        complete(StatusCodes.NoContent)
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
