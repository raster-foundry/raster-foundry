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
import com.azavea.rf.database.{ActionRunner, Database}
import com.azavea.rf.database.tables._
import com.azavea.rf.common.AWSBatch
import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.GeoJsonCodec._
import com.lonelyplanet.akka.http.extensions.{PaginationDirectives, PageRequest}
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import io.circe._
import io.circe.Json
import io.circe.generic.JsonCodec
import io.circe.optics.JsonPath._
import kamon.akka.http.KamonTraceDirectives

import com.typesafe.scalalogging.LazyLogging

@JsonCodec
case class BulkAcceptParams(sceneIds: List[UUID])

@JsonCodec
case class AnnotationFeatureCollectionCreate (
  features: Seq[Annotation.GeoJSONFeatureCreate]
)

trait ProjectRoutes extends Authentication
    with QueryParametersCommon
    with SceneQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with AWSBatch
    with UserErrorHandler
    with KamonTraceDirectives
    with ActionRunner
    with LazyLogging {

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
      pathPrefix("labels") {
        pathEndOrSingleSlash {
          get {
            traceName("project-list-labels") {
              listLabels(projectId)
            }
          }
        }
      } ~
      pathPrefix("annotations") {
        pathEndOrSingleSlash {
          get {
            traceName("projects-list-annotations") {
              listAnnotations(projectId)
            }
          } ~
          post {
            traceName("projects-create-annotations") {
              createAnnotation(projectId)
            }
          } ~
            delete {
              traceName("projects-delete-annotations") {
                deleteProjectAnnotations(projectId)
              }
            }
        } ~
        pathPrefix(JavaUUID) { annotationId =>
          pathEndOrSingleSlash {
            get {
              traceName("projects-get-annotation") {
                getAnnotation(annotationId)
              }
            } ~
            put {
              traceName("projects-update-annotation") {
                updateAnnotation(annotationId)
              }
            } ~
            delete {
              traceName("projects-delete-annotation") {
                deleteAnnotation(annotationId)
              }
            }
          }
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
              createAOI(projectId)
            }
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

  def listLabels(projectId: UUID): Route = authenticate { user =>
    complete {
      Annotations.listProjectLabels(projectId, user)
    }
  }

  def listAnnotations(projectId: UUID): Route = authenticate { user =>
    (withPagination & annotationQueryParams) { (page: PageRequest, queryParams: AnnotationQueryParameters) =>
      complete {
        list[Annotation](
          Annotations.listAnnotations(page.offset, page.limit, queryParams, projectId, user),
          page.offset, page.limit
        ) map { p => {
            fromPaginatedResponseToGeoJson[Annotation, Annotation.GeoJSON](p)
          }
        }
      }
    }
  }

  def createAnnotation(projectId: UUID): Route = authenticate { user =>
    entity(as[AnnotationFeatureCollectionCreate]) { fc =>
      val annotationsCreate = fc.features map { _.toAnnotationCreate }
      complete {
        Annotations.insertAnnotations(annotationsCreate, projectId, user)
      }
    }
  }

  def getAnnotation(annotationId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        readOne[Annotation](Annotations.getAnnotation(annotationId, user)) map { _ map { _.toGeoJSONFeature } }
      }
    }
  }

  def updateAnnotation(annotationId: UUID): Route = authenticate { user =>
    entity(as[Annotation.GeoJSON]) { updatedAnnotation: Annotation.GeoJSON =>
      authorize(user.isInRootOrSameOrganizationAs(updatedAnnotation.properties)) {
        onSuccess(update(Annotations.updateAnnotation(updatedAnnotation, annotationId, user))) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteAnnotation(annotationId: UUID): Route = authenticate { user =>
    onSuccess(drop(Annotations.deleteAnnotation(annotationId, user))) {
      completeSingleOrNotFound
    }
  }

  def deleteProjectAnnotations(projectId: UUID): Route = authenticate { user =>
    onSuccess(drop(Annotations.deleteProjectAnnotations(projectId, user))) {
      completeSomeOrNotFound
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
      val scenesFuture = Projects.addScenesToProject(sceneIds, projectId, user).map { scenes =>
        val scenesToKickoff = scenes.filter(scene =>
          scene.statusFields.ingestStatus == IngestStatus.ToBeIngested || (
            scene.statusFields.ingestStatus == IngestStatus.Ingesting &&
              scene.modifiedAt.before(
                new Timestamp((new Date(System.currentTimeMillis()-1*24*60*60*1000)).getTime)
              )
          )
        )
        logger.info(s"Kicking off ${scenesToKickoff.size} scene ingests")
        scenesToKickoff.map(_.id).map(kickoffSceneIngest)
        scenes
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
