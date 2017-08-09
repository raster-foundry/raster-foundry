package com.azavea.rf.api.project

import java.sql.Timestamp
import java.util.{Date, UUID}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Success, Failure}

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling._
import com.azavea.rf.api.scene._
import com.azavea.rf.api.utils.queryparams.QueryParametersCommon
import com.azavea.rf.common.{Authentication, CommonHandlers, UserErrorHandler}
import com.azavea.rf.database.Database
import com.azavea.rf.database.query._
import com.azavea.rf.database.tables._
import com.azavea.rf.common.Airflow
import com.azavea.rf.datamodel._
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe._
import io.circe.generic.JsonCodec
import kamon.akka.http.KamonTraceDirectives

@JsonCodec
case class BulkAcceptParams(sceneIds: List[UUID])

trait ProjectRoutes extends Authentication
    with QueryParametersCommon
    with SceneQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with Airflow
    with UserErrorHandler
    with KamonTraceDirectives {

  implicit def database: Database

  val BULK_OPERATION_MAX_LIMIT = 100

  val projectRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get {
        traceName("projects-list") {
          listProjects
        }
      } ~
      post {
        traceName("projects-create") {
          createProject
        }
      }
    } ~
    pathPrefix(JavaUUID) { projectId =>
      pathEndOrSingleSlash {
        get {
          traceName("projects-detail") {
            getProject(projectId)
          }
        } ~
        put {
          traceName("projects-update") {
            updateProject(projectId)
          }
        } ~
        delete {
          traceName("projects-delete") {
            deleteProject(projectId) }
        }
      } ~
      pathPrefix("areas-of-interest") {
        pathEndOrSingleSlash {
          get {
            traceName("projects-list-areas-of-interest") {
              listAOIs(projectId)
            }
          } ~
          post {
            traceName("projects-create-areas-of-interest") {
              createAOI(projectId) }
          }
        }
      } ~
      pathPrefix("scenes") {
        pathEndOrSingleSlash {
          get {
            traceName("project-list-scenes") {
              listProjectScenes(projectId)
            }
          } ~
          post {
            traceName("project-add-scenes-list") {
              addProjectScenes(projectId)
            }
          } ~
          put {
            traceName("project-update-scenes-list") {
              updateProjectScenes(projectId)
            }
          } ~
          delete {
            traceName("project-delete-scenes-list") {
              deleteProjectScenes(projectId)
            }
          }
        } ~
        pathPrefix("bulk-add-from-query") {
          pathEndOrSingleSlash {
            post {
              traceName("project-add-scenes-from-query") {
                addProjectScenesFromQueryParams(projectId)
              }
            }
          }
        } ~
        pathPrefix("accept") {
          post {
            traceName("project-accept-scenes-list") {
              acceptScenes(projectId)
            }
          }
        } ~
        pathPrefix(JavaUUID) { sceneId =>
          pathPrefix("accept") {
            post {
              traceName("project-accept-scene") {
                acceptScene(projectId, sceneId)
              }
            }
          }
        }
      } ~
      pathPrefix("mosaic") {
        pathEndOrSingleSlash {
          get {
            traceName("project-get-mosaic-definition") {
              getProjectMosaicDefinition(projectId)
            }
          }
        } ~
        pathPrefix(JavaUUID) { sceneId =>
          get {
            traceName("project-get-scene-color-corrections") {
              getProjectSceneColorCorrectParams(projectId, sceneId)
            }
          } ~
          put {
            traceName("project-set-scene-color-corrections") {
              setProjectSceneColorCorrectParams(projectId, sceneId)
            }
          }
        } ~
        pathPrefix("bulk-update-color-corrections") {
          pathEndOrSingleSlash {
            post {
              traceName("project-bulk-update-color-corrections") {
                setProjectScenesColorCorrectParams(projectId)
              }
            }
          }
        }
      } ~
      pathPrefix("order") {
        pathEndOrSingleSlash {
          get {
            traceName("projects-get-scene-order") {
              listProjectSceneOrder(projectId)
            }
          } ~
          put {
            traceName("projects-set-scene-order") {
              setProjectSceneOrder(projectId)
            }
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
      authorize(user.isInRootOrSameOrganizationAs(newProject)) {
        onSuccess(Projects.insertProject(newProject.toProject(user))) { project =>
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

  def listAOIs(projectId: UUID): Route = authenticate { user =>
    withPagination { page =>
      complete {
        Projects.listAOIs(projectId, page, user)
      }
    }
  }

  def createAOI(projectId: UUID): Route = authenticate { user =>
    entity(as[AOI.Create]) { aoi =>
      authorize(user.isInRootOrSameOrganizationAs(aoi)) {
        onSuccess({
          for {
            a <- AOIs.insertAOI(aoi.toAOI(user))
            _ <- AoisToProjects.insert(AoiToProject(a.id, projectId, true, new Timestamp((new Date).getTime)))
          } yield a
        }) { a =>
          complete(StatusCodes.Created, a)
        }
      }
    }
  }

  def acceptScene(projectId: UUID, sceneId: UUID): Route = authenticate { user =>
    complete {
      ScenesToProjects.acceptScene(projectId, sceneId)
    }
  }

  def acceptScenes(projectId: UUID): Route = authenticate { user =>
    entity(as[BulkAcceptParams]) { sceneParams =>
      onSuccess(
        Future.sequence(
          sceneParams.sceneIds.map(s => ScenesToProjects.acceptScene(projectId, s)))
      ) { _ =>
        complete(StatusCodes.NoContent)
      }
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
      val scenesFuture = Projects.addScenesToProject(sceneIds, projectId, user)
      scenesFuture.map { scenes =>
        val scenesToKickoff = scenes.filter(_.statusFields.ingestStatus == IngestStatus.ToBeIngested)
        scenesToKickoff.map(_.id).map(kickoffSceneIngest)
      }
      complete {
        scenesFuture
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
