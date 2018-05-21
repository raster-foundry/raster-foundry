package com.azavea.rf.api.project

import java.sql.Timestamp
import java.util.{Calendar, Date, UUID}

import com.amazonaws.services.s3.AmazonS3URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}
import akka.http.scaladsl.model.{Uri, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling._
import cats.data.NonEmptyList
import cats.effect.IO
import cats.implicits._
import com.azavea.rf.api.scene._
import com.azavea.rf.api.utils.queryparams.QueryParametersCommon
import com.azavea.rf.api.utils.Config
import com.azavea.rf.common.{Authentication, CommonHandlers, RollbarNotifier, UserErrorHandler}
import com.azavea.rf.common.AWSBatch
import com.azavea.rf.common.S3._
import com.azavea.rf.database._
import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.GeoJsonCodec._
import com.azavea.rf.datamodel.Annotation
import com.lonelyplanet.akka.http.extensions.{PageRequest, PaginationDirectives}
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import io.circe._
import io.circe.Json
import io.circe.generic.JsonCodec
import io.circe.optics.JsonPath._
import kamon.akka.http.KamonTraceDirectives
import better.files._

import com.typesafe.scalalogging.LazyLogging
import doobie.util.transactor.Transactor
import com.azavea.rf.database.filter.Filterables._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._


@JsonCodec
case class BulkAcceptParams(sceneIds: List[UUID])

@JsonCodec
case class AnnotationFeatureCollectionCreate (
  features: Seq[Annotation.GeoJSONFeatureCreate]
)

trait ProjectRoutes extends Authentication
    with Config
    with QueryParametersCommon
    with SceneQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with AWSBatch
    with UserErrorHandler
    with RollbarNotifier
    with KamonTraceDirectives
    with LazyLogging {

  val xa: Transactor[IO]

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
        pathPrefix("shapefile") {
          get {
            traceName("project-annotations-shapefile") {
              exportAnnotationShapefile(projectId)
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
        ProjectDao.query.filter(projectQueryParameters).filter(user).page(page).transact(xa).unsafeToFuture
      }
    }
  }

  def createProject: Route = authenticate { user =>
    entity(as[Project.Create]) { newProject =>
      authorize(user.isInRootOrSameOrganizationAs(newProject)) {
        onSuccess(ProjectDao.insertProject(newProject, user).transact(xa).unsafeToFuture) { project =>
          complete(StatusCodes.Created, project)
        }
      }
    }
  }

  def getProject(projectId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        ProjectDao.query.filter(user).filter(projectId).selectOption.transact(xa).unsafeToFuture
      }
    }
  }

  def updateProject(projectId: UUID): Route = authenticate { user =>
    entity(as[Project]) { updatedProject =>
      authorize(user.isInRootOrSameOrganizationAs(updatedProject)) {
        onSuccess(ProjectDao.updateProject(updatedProject, projectId, user).transact(xa).unsafeToFuture) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteProject(projectId: UUID): Route = authenticate { user =>
    onSuccess(ProjectDao.deleteProject(projectId, user).transact(xa).unsafeToFuture) {
      completeSingleOrNotFound
    }
  }

  def listLabels(projectId: UUID): Route = authenticate { user =>
    complete {
      AnnotationDao.listProjectLabels(projectId, user).transact(xa).unsafeToFuture
    }
  }

  def listAnnotations(projectId: UUID): Route = authenticate { user =>
    (withPagination & annotationQueryParams) { (page: PageRequest, queryParams: AnnotationQueryParameters) =>
      complete {
        AnnotationDao.query.filter(fr"project_id=$projectId").filter(queryParams).filter(user).page(page).transact(xa).unsafeToFuture
          .map { p => {
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
        AnnotationDao.insertAnnotations(annotationsCreate.toList, projectId, user).transact(xa).unsafeToFuture
          .map { annotations: List[Annotation] => fromSeqToFeatureCollection[Annotation, Annotation.GeoJSON](annotations) }
      }
    }
  }

  def exportAnnotationShapefile(projectId: UUID): Route = authenticate { user =>
    onSuccess(AnnotationDao.query.filter(fr"project_id=$projectId").ownerFilter(user).list.transact(xa).unsafeToFuture) { annotations =>
      annotations match {
        case annotation: List[Annotation] => {
          val zipfile: File = AnnotationShapefileService.annotationsToShapefile(annotations)
          val cal:Calendar = Calendar.getInstance()
          cal.add(Calendar.DAY_OF_YEAR, 1)
          val key:AmazonS3URI = new AmazonS3URI(user.getDefaultAnnotationShapefileSource(dataBucket))
          putObject(dataBucket, key.toString(), zipfile.toJava).setExpirationTime(cal.getTime())
          zipfile.delete(true)
          complete(getSignedUrl(dataBucket, key.toString).toString())
        }
        case _ => complete(throw new Exception("Annotations do not exist or are not accessible by this user"))
      }
    }

  }

  def getAnnotation(annotationId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        AnnotationDao.query.filter(user).filter(annotationId).selectOption.transact(xa).unsafeToFuture.map {
          _ map { _.toGeoJSONFeature }
        }
      }
    }
  }

  def updateAnnotation(annotationId: UUID): Route = authenticate { user =>
    entity(as[Annotation.GeoJSON]) { updatedAnnotation: Annotation.GeoJSON =>
      authorize(user.isInRootOrSameOrganizationAs(updatedAnnotation.properties)) {
        onSuccess(AnnotationDao.updateAnnotation(updatedAnnotation.toAnnotation, annotationId, user).transact(xa).unsafeToFuture) { count =>
          completeSingleOrNotFound(count)
        }
      }
    }
  }

  def deleteAnnotation(annotationId: UUID): Route = authenticate { user =>
    onSuccess(AnnotationDao.query.filter(fr"id = ${annotationId}").delete.transact(xa).unsafeToFuture) {
      completeSingleOrNotFound
    }
  }

  def deleteProjectAnnotations(projectId: UUID): Route = authenticate { user =>
    onSuccess(AnnotationDao.query.filter(fr"project_id = ${projectId}").delete.transact(xa).unsafeToFuture) {
      completeSomeOrNotFound
    }
  }

  def listAOIs(projectId: UUID): Route = authenticate { user =>
    withPagination { page =>
      complete {
        AoiDao.listAOIs(projectId, user, page).transact(xa).unsafeToFuture
      }
    }
  }

  def createAOI(projectId: UUID): Route = authenticate { user =>
    entity(as[AOI.Create]) { aoi =>
      authorize(user.isInRootOrSameOrganizationAs(aoi)) {
        onSuccess(AoiDao.createAOI(aoi.toAOI(projectId, user), projectId, user: User).transact(xa).unsafeToFuture()) { a =>
          complete(StatusCodes.Created, a)
        }
      }
    }
  }

  def acceptScene(projectId: UUID, sceneId: UUID): Route = authenticate { user =>
    complete {
      SceneToProjectDao.acceptScene(projectId, sceneId).transact(xa).unsafeToFuture
    }
  }

  def acceptScenes(projectId: UUID): Route = authenticate { user =>
    entity(as[BulkAcceptParams]) { sceneParams =>
      sceneParams.sceneIds.toNel.map(ids => ProjectDao.addScenesToProject(ids, projectId, user)) match {
        case Some(addQuery) => {
          onSuccess(addQuery.transact(xa).unsafeToFuture) {
            numAdded => complete(sceneParams.sceneIds)
          }
        }
        case _ => complete(StatusCodes.BadRequest)
      }
    }
  }

  def listProjectScenes(projectId: UUID): Route = authenticate { user =>
    (withPagination & sceneQueryParameters) { (page, sceneParams) =>
      complete {
        SceneWithRelatedDao.listProjectScenes(projectId, page, sceneParams, user).transact(xa).unsafeToFuture
      }
    }
  }

  /** List a project's scenes according to their manually defined ordering */
  def listProjectSceneOrder(projectId: UUID): Route = authenticate { user =>
    withPagination { page =>
      complete {
        ProjectDao.listProjectSceneOrder(projectId, page, user).transact(xa).unsafeToFuture
      }
    }
  }

  /** Set the manually defined z-ordering for scenes within a given project */
  def setProjectSceneOrder(projectId: UUID): Route = authenticate { user =>
    entity(as[Seq[UUID]]) { sceneIds =>
      if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
        complete(StatusCodes.RequestEntityTooLarge)
      }

      onSuccess(SceneToProjectDao.setManualOrder(projectId, sceneIds).transact(xa).unsafeToFuture) { updatedOrder =>
        complete(StatusCodes.NoContent)
      }
    }
  }

  /** Get the color correction paramters for a project/scene pairing */
  def getProjectSceneColorCorrectParams(projectId: UUID, sceneId: UUID) = authenticate { user =>
    complete {
      SceneToProjectDao.getColorCorrectParams(projectId, sceneId).transact(xa).unsafeToFuture
    }
  }

  /** Set color correction parameters for a project/scene pairing */
  def setProjectSceneColorCorrectParams(projectId: UUID, sceneId: UUID) = authenticate { user =>
    entity(as[ColorCorrect.Params]) { ccParams =>
      onSuccess(SceneToProjectDao.setColorCorrectParams(projectId, sceneId, ccParams).transact(xa).unsafeToFuture) { sceneToProject =>
        complete(StatusCodes.NoContent)
      }
    }
  }

  /** Set color correction parameters for a list of scenes */
  def setProjectScenesColorCorrectParams(projectId: UUID) = authenticate { user =>
    entity(as[BatchParams]) { params =>
      onSuccess(SceneToProjectDao.setColorCorrectParamsBatch(projectId, params).transact(xa).unsafeToFuture) { scenesToProject =>
        complete(StatusCodes.NoContent)
      }
    }
  }

  /** Get the information which defines mosaicing behavior for each scene in a given project */
  def getProjectMosaicDefinition(projectId: UUID) = authenticate { user =>
    rejectEmptyResponse {
      complete {
        SceneToProjectDao.getMosaicDefinition(projectId).transact(xa).unsafeToFuture
      }
    }
  }

  def addProjectScenes(projectId: UUID): Route = authenticate { user =>
    entity(as[NonEmptyList[UUID]]) { sceneIds =>
      if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
        complete(StatusCodes.RequestEntityTooLarge)
      }
      val scenesAdded = ProjectDao.addScenesToProject(sceneIds, projectId, user)
      val scenesToIngest = SceneWithRelatedDao.getScenesToIngest(projectId)
      val x: ConnectionIO[List[Scene.WithRelated]] = for {
        _ <- scenesAdded
        scenes <- scenesToIngest
      } yield {
        logger.info(s"Kicking off ${scenes.size} scene ingests")
        scenes.map(_.id).map(kickoffSceneIngest)
        scenes
      }

      complete{ x.transact(xa).unsafeToFuture }
    }
  }

  def addProjectScenesFromQueryParams(projectId: UUID): Route = authenticate { user =>
    entity(as[CombinedSceneQueryParams]) { combinedSceneQueryParams =>
      onSuccess(ProjectDao.addScenesToProjectFromQuery(combinedSceneQueryParams, projectId, user).transact(xa).unsafeToFuture()) {
        scenesAdded => {
          val ingestsKickoff = SceneWithRelatedDao.getScenesToIngest(projectId) map {
            (toIngest: List[Scene.WithRelated]) => {
              logger.info(s"Kicking off ${toIngest.length} scene ingests from query parameter add")
              toIngest map { (scene: Scene.WithRelated) => kickoffSceneIngest(scene.id) }
            }
          }
          ingestsKickoff.transact(xa).unsafeRunAsync(
            (result: Either[Throwable, List[Unit]]) => {
              result match {
                case Left(error) => sendError(error)
                case _ => ()
              }
            }
          )
          complete((StatusCodes.Created, scenesAdded))
        }
      }
    }
  }

  def updateProjectScenes(projectId: UUID): Route = authenticate { user =>
    entity(as[Seq[UUID]]) { sceneIds =>
      if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
        complete(StatusCodes.RequestEntityTooLarge)
      }

      sceneIds.toList.toNel match {
        case Some(ids) => {
          complete {
            ProjectDao.replaceScenesInProject(ids, projectId, user).transact(xa).unsafeToFuture()
          }
        }
        case _ => complete(StatusCodes.BadRequest)
      }
    }
  }

  def deleteProjectScenes(projectId: UUID): Route = authenticate { user =>
    entity(as[Seq[UUID]]) { sceneIds =>
      if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
        complete(StatusCodes.RequestEntityTooLarge)
      }

      onSuccess(ProjectDao.deleteScenesFromProject(sceneIds.toList, projectId).transact(xa).unsafeToFuture()) {
        _ => complete(StatusCodes.NoContent)
      }
    }
  }
}
