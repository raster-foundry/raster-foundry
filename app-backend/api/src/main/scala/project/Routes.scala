package com.azavea.rf.api.project

import java.util.{Calendar, UUID}

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileInfo
import better.files.{File => ScalaFile}
import cats.data.NonEmptyList
import cats.effect.IO
import cats.implicits._
import com.amazonaws.services.s3.AmazonS3URI
import com.azavea.rf.api.scene._
import com.azavea.rf.api.utils.Config
import com.azavea.rf.api.utils.queryparams.QueryParametersCommon
import com.azavea.rf.authentication.Authentication
import com.azavea.rf.common.S3._
import com.azavea.rf.common.utils.Shapefile
import com.azavea.rf.common.{
  AWSBatch,
  CommonHandlers,
  RollbarNotifier,
  UserErrorHandler
}
import com.azavea.rf.database._
import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.datamodel.GeoJsonCodec._
import com.azavea.rf.datamodel.{Annotation, _}
import com.lonelyplanet.akka.http.extensions.{PageRequest, PaginationDirectives}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import geotrellis.shapefile.ShapeFileReader
import io.circe.generic.JsonCodec
import kamon.akka.http.KamonTraceDirectives

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success
@JsonCodec
final case class BulkAcceptParams(sceneIds: List[UUID])

@JsonCodec
final case class AnnotationFeatureCollectionCreate(
    features: Seq[Annotation.GeoJSONFeatureCreate]
)

trait ProjectRoutes
    extends Authentication
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
                deleteProject(projectId)
              }
            }
        } ~
          pathPrefix("project-color-mode") {
            pathEndOrSingleSlash {
              post {
                traceName("project-set-color-mode") {
                  setProjectColorMode(projectId)
                }
              }
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
          pathPrefix("annotation-groups") {
            pathEndOrSingleSlash {
              get {
                traceName("projects-list-annotation-groups") {
                  listAnnotationGroups(projectId)
                }
              } ~
                post {
                  traceName("projects-create-annotation-group") {
                    createAnnotationGroup(projectId)
                  }
                }
            } ~
              pathPrefix(JavaUUID) { annotationGroupId =>
                pathEndOrSingleSlash {
                  get {
                    traceName("projects-get-annotation-group") {
                      getAnnotationGroup(projectId, annotationGroupId)
                    }
                  } ~
                    put {
                      traceName("projects-update-annotation-group") {
                        updateAnnotationGroup(projectId, annotationGroupId)
                      }
                    } ~
                    delete {
                      traceName("projects-delete-annotation-group") {
                        deleteAnnotationGroup(projectId, annotationGroupId)
                      }
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
                pathEndOrSingleSlash {
                  get {
                    traceName("project-annotations-shapefile") {
                      exportAnnotationShapefile(projectId)
                    }
                  } ~
                    post {
                      traceName("project-annotations-shapefile-upload") {
                        authenticate { user =>
                          val tempFile = ScalaFile.newTemporaryFile()
                          tempFile.deleteOnExit()
                          val response =
                            storeUploadedFile("name", (_) => tempFile.toJava) {
                              (m, _) =>
                                processShapefile(projectId, tempFile, m)
                            }
                          tempFile.delete()
                          response
                        }
                      }
                    }
                } ~
                  pathPrefix("import") {
                    pathEndOrSingleSlash {
                      (post & formFieldMap) { fields =>
                        traceName(
                          "project-annotations-shapefile-import-with-fields") {
                          authenticate { user =>
                            val tempFile = ScalaFile.newTemporaryFile()
                            tempFile.deleteOnExit()
                            val response =
                              storeUploadedFile("shapefile",
                                                (_) => tempFile.toJava) {
                                (m, _) =>
                                  processShapefile(projectId,
                                                   tempFile,
                                                   m,
                                                   Some(fields))
                              }
                            tempFile.delete()
                            response
                          }
                        }
                      }
                    }
                  }
              } ~
              pathPrefix(JavaUUID) { annotationId =>
                pathEndOrSingleSlash {
                  get {
                    traceName("projects-get-annotation") {
                      getAnnotation(projectId, annotationId)
                    }
                  } ~
                    put {
                      traceName("projects-update-annotation") {
                        updateAnnotation(projectId, annotationId)
                      }
                    } ~
                    delete {
                      traceName("projects-delete-annotation") {
                        deleteAnnotation(projectId, annotationId)
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
          pathPrefix("datasources") {
            pathEndOrSingleSlash {
              get {
                traceName("project-list-datasources") {
                  listProjectDatasources(projectId)
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
              put {
                traceName("projects-set-scene-order") {
                  setProjectSceneOrder(projectId)
                }
              }
            } // ~
            // pathPrefix("move") {
            //   pathPrefix(IntNumber) { from =>
            //     pathPrefix(IntNumber) { to =>
            //       traceName("projects-move-scene-order") {
            //         moveProjectScene(projectId, from, to)
            //       }
            //     }
            //   }
            // }
          } ~
          pathPrefix("permissions") {
            pathEndOrSingleSlash {
              put {
                traceName("replace-project-permissions") {
                  replaceProjectPermissions(projectId)
                }
              }
            } ~
              post {
                traceName("add-project-permission") {
                  addProjectPermission(projectId)
                }
              } ~
              get {
                traceName("list-project-permissions") {
                  listProjectPermissions(projectId)
                }
              } ~
              delete {
                deleteProjectPermissions(projectId)
              }
          } ~
          pathPrefix("actions") {
            pathEndOrSingleSlash {
              get {
                traceName("list-user-allowed-actions") {
                  listUserProjectActions(projectId)
                }
              }
            }
          }
      }
  }

  def listProjects: Route = authenticate { user =>
    (withPagination & projectQueryParameters) {
      (page, projectQueryParameters) =>
        complete {
          ProjectDao
            .listProjects(page, projectQueryParameters, user)
            .transact(xa)
            .unsafeToFuture
        }
    }
  }

  def createProject: Route = authenticate { user =>
    entity(as[Project.Create]) { newProject =>
      onSuccess(
        ProjectDao
          .insertProject(newProject, user)
          .transact(xa)
          .unsafeToFuture) { project =>
        complete(StatusCodes.Created, project)
      }
    }
  }

  def getProject(projectId: UUID): Route = {
    onComplete(
      ProjectDao.isProjectPublic(projectId).transact(xa).unsafeToFuture
    ) {
      case Success(true) =>
        rejectEmptyResponse {
          complete {
            ProjectDao.query
              .filter(projectId)
              .selectOption
              .transact(xa)
              .unsafeToFuture
          }
        }
      case _ =>
        authenticate { user =>
          authorizeAsync {
            ProjectDao
              .authorized(user, ObjectType.Project, projectId, ActionType.View)
              .transact(xa)
              .unsafeToFuture
          } {
            rejectEmptyResponse {
              complete {
                ProjectDao.query
                  .filter(projectId)
                  .selectOption
                  .transact(xa)
                  .unsafeToFuture
              }
            }
          }
        }
    }
  }

  def updateProject(projectId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[Project]) { updatedProject =>
        onSuccess(
          ProjectDao
            .updateProject(updatedProject, projectId, user)
            .transact(xa)
            .unsafeToFuture) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteProject(projectId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.Delete)
        .transact(xa)
        .unsafeToFuture
    } {
      onSuccess(ProjectDao.deleteProject(projectId).transact(xa).unsafeToFuture) {
        completeSingleOrNotFound
      }
    }
  }

  def listLabels(projectId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      complete {
        AnnotationDao.listProjectLabels(projectId).transact(xa).unsafeToFuture
      }
    }
  }

  def listAnnotationGroups(projectId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      complete {
        AnnotationGroupDao
          .listAnnotationGroupsForProject(projectId)
          .transact(xa)
          .unsafeToFuture
      }
    }
  }

  def createAnnotationGroup(projectId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.Annotate)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[AnnotationGroup.Create]) { agCreate =>
        complete {
          AnnotationGroupDao
            .createAnnotationGroup(projectId, agCreate, user)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def getAnnotationGroup(projectId: UUID, agId: UUID): Route = authenticate {
    user =>
      authorizeAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.View)
          .transact(xa)
          .unsafeToFuture
      } {
        complete {
          AnnotationGroupDao
            .getAnnotationGroup(projectId, agId)
            .transact(xa)
            .unsafeToFuture
        }
      }
  }

  def updateAnnotationGroup(projectId: UUID, agId: UUID): Route = authenticate {
    user =>
      authorizeAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Annotate)
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[AnnotationGroup]) { annotationGroup =>
          complete {
            AnnotationGroupDao
              .updateAnnotationGroup(annotationGroup, agId, user)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
  }

  def deleteAnnotationGroup(projectId: UUID, agId: UUID): Route = authenticate {
    user =>
      authorizeAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Annotate)
          .transact(xa)
          .unsafeToFuture
      } {
        complete {
          AnnotationGroupDao
            .deleteAnnotationGroup(projectId, agId)
            .transact(xa)
            .unsafeToFuture
        }
      }
  }

  def listAnnotations(projectId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      (withPagination & annotationQueryParams) {
        (page: PageRequest, queryParams: AnnotationQueryParameters) =>
          complete {
            AnnotationDao.query
              .filter(fr"project_id=$projectId")
              .filter(queryParams)
              .page(page, fr"")
              .transact(xa)
              .unsafeToFuture
              .map { p =>
                {
                  fromPaginatedResponseToGeoJson[Annotation,
                                                 Annotation.GeoJSON](p)
                }
              }
          }
      }
    }
  }

  def createAnnotation(projectId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.Annotate)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[AnnotationFeatureCollectionCreate]) { fc =>
        val annotationsCreate = fc.features map { _.toAnnotationCreate }
        complete {
          AnnotationDao
            .insertAnnotations(annotationsCreate.toList, projectId, user)
            .transact(xa)
            .unsafeToFuture
            .map { annotations: List[Annotation] =>
              fromSeqToFeatureCollection[Annotation, Annotation.GeoJSON](
                annotations)
            }
        }
      }
    }
  }

  def exportAnnotationShapefile(projectId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      onSuccess(
        AnnotationDao.query
          .filter(fr"project_id=$projectId")
          .list
          .transact(xa)
          .unsafeToFuture) {
        case annotations @ (annotation: List[Annotation]) => {
          val zipfile: ScalaFile =
            AnnotationShapefileService.annotationsToShapefile(annotations)
          val cal: Calendar = Calendar.getInstance()
          cal.add(Calendar.DAY_OF_YEAR, 1)
          val key: AmazonS3URI = new AmazonS3URI(
            user.getDefaultAnnotationShapefileSource(dataBucket))
          putObject(dataBucket, key.toString, zipfile.toJava)
            .setExpirationTime(cal.getTime)
          zipfile.delete(true)
          complete(getSignedUrl(dataBucket, key.toString).toString())
        }
        case _ =>
          complete(
            throw new Exception(
              "Annotations do not exist or are not accessible by this user"))
      }
    }
  }

  def getAnnotation(projectId: UUID, annotationId: UUID): Route = authenticate {
    user =>
      authorizeAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.View)
          .transact(xa)
          .unsafeToFuture
      } {
        rejectEmptyResponse {
          complete {
            AnnotationDao.query
              .filter(annotationId)
              .selectOption
              .transact(xa)
              .unsafeToFuture
              .map {
                _ map { _.toGeoJSONFeature }
              }
          }
        }
      }
  }

  def updateAnnotation(projectId: UUID, annotationId: UUID): Route =
    authenticate { user =>
      authorizeAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Annotate)
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[Annotation.GeoJSON]) {
          updatedAnnotation: Annotation.GeoJSON =>
            onSuccess(
              AnnotationDao
                .updateAnnotation(updatedAnnotation.toAnnotation, user)
                .transact(xa)
                .unsafeToFuture) { count =>
              completeSingleOrNotFound(count)
            }
        }
      }
    }

  def deleteAnnotation(projectId: UUID, annotationId: UUID): Route =
    authenticate { user =>
      authorizeAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Annotate)
          .transact(xa)
          .unsafeToFuture
      } {
        onSuccess(
          AnnotationDao.query
            .filter(annotationId)
            .delete
            .transact(xa)
            .unsafeToFuture) {
          completeSingleOrNotFound
        }
      }
    }

  def deleteProjectAnnotations(projectId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.Annotate)
        .transact(xa)
        .unsafeToFuture
    } {
      onSuccess(
        AnnotationDao.query
          .filter(fr"project_id = ${projectId}")
          .delete
          .transact(xa)
          .unsafeToFuture) {
        completeSomeOrNotFound
      }
    }
  }

  def listAOIs(projectId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      withPagination { page =>
        complete {
          AoiDao.listAOIs(projectId, page).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def createAOI(projectId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[AOI.Create]) { aoi =>
        onSuccess(
          AoiDao
            .createAOI(aoi.toAOI(projectId, user), user: User)
            .transact(xa)
            .unsafeToFuture()) { a =>
          complete(StatusCodes.Created, a)
        }
      }
    }
  }

  def acceptScene(projectId: UUID, sceneId: UUID): Route = authenticate {
    user =>
      authorizeAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
      } {
        complete {
          SceneToProjectDao
            .acceptScene(projectId, sceneId)
            .transact(xa)
            .unsafeToFuture
        }
      }
  }

  def acceptScenes(projectId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[List[UUID]]) { sceneIds =>
        if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
          complete(StatusCodes.RequestEntityTooLarge)
        }

        onSuccess(
          SceneToProjectDao
            .acceptScenes(projectId, sceneIds)
            .transact(xa)
            .unsafeToFuture) { updatedOrder =>
          complete(StatusCodes.NoContent)
        }
      }
    }
  }

  def listProjectScenes(projectId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      (withPagination & sceneQueryParameters) { (page, sceneParams) =>
        complete {
          ProjectScenesDao
            .listProjectScenes(projectId, page, sceneParams)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def listProjectDatasources(projectId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      complete {
        ProjectDatasourcesDao
          .listProjectDatasources(projectId)
          .transact(xa)
          .unsafeToFuture
      }
    }
  }

  def moveProjectScene(projectId: UUID, from: Int, to: Int): Route =
    authenticate { user =>
      authorizeAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
      } {
        onSuccess(
          SceneToProjectDao
            .moveSceneOrder(projectId, from, to)
            .transact(xa)
            .unsafeToFuture) { _ =>
          complete(StatusCodes.NoContent)
        }
      }
    }

  /** Set the manually defined z-ordering for scenes within a given project */
  def setProjectSceneOrder(projectId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[Seq[UUID]]) { sceneIds =>
        if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
          complete(StatusCodes.RequestEntityTooLarge)
        }

        onSuccess(
          SceneToProjectDao
            .setManualOrder(projectId, sceneIds)
            .transact(xa)
            .unsafeToFuture) { updatedOrder =>
          complete(StatusCodes.NoContent)
        }
      }
    }
  }

  /** Get the color correction paramters for a project/scene pairing */
  def getProjectSceneColorCorrectParams(projectId: UUID, sceneId: UUID) =
    authenticate { user =>
      authorizeAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.View)
          .transact(xa)
          .unsafeToFuture
      } {
        complete {
          SceneToProjectDao
            .getColorCorrectParams(projectId, sceneId)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }

  /** Set color correction parameters for a project/scene pairing */
  def setProjectSceneColorCorrectParams(projectId: UUID, sceneId: UUID) =
    authenticate { user =>
      authorizeAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[ColorCorrect.Params]) { ccParams =>
          onSuccess(
            SceneToProjectDao
              .setColorCorrectParams(projectId, sceneId, ccParams)
              .transact(xa)
              .unsafeToFuture) { sceneToProject =>
            complete(StatusCodes.NoContent)
          }
        }
      }
    }

  def setProjectColorMode(projectId: UUID) = authenticate { user =>
    authorizeAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[ProjectColorModeParams]) { colorBands =>
        onSuccess(
          SceneToProjectDao
            .setProjectColorBands(projectId, colorBands)
            .transact(xa)
            .unsafeToFuture) { _ =>
          complete(StatusCodes.NoContent)
        }
      }
    }
  }

  /** Set color correction parameters for a list of scenes */
  def setProjectScenesColorCorrectParams(projectId: UUID) = authenticate {
    user =>
      authorizeAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[BatchParams]) { params =>
          onSuccess(
            SceneToProjectDao
              .setColorCorrectParamsBatch(projectId, params)
              .transact(xa)
              .unsafeToFuture) { scenesToProject =>
            complete(StatusCodes.NoContent)
          }
        }
      }
  }

  /** Get the information which defines mosaicing behavior for each scene in a given project */
  def getProjectMosaicDefinition(projectId: UUID) = authenticate { user =>
    authorizeAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      rejectEmptyResponse {
        complete {
          SceneToProjectDao
            .getMosaicDefinition(projectId)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def addProjectScenes(projectId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[NonEmptyList[UUID]]) { sceneIds =>
        if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
          complete(StatusCodes.RequestEntityTooLarge)
        }
        val scenesAdded =
          ProjectDao.addScenesToProject(sceneIds, projectId, true)

        complete { scenesAdded.transact(xa).unsafeToFuture }
      }
    }
  }

  def addProjectScenesFromQueryParams(projectId: UUID): Route = authenticate {
    user =>
      authorizeAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[CombinedSceneQueryParams]) { combinedSceneQueryParams =>
          onSuccess(
            ProjectDao
              .addScenesToProjectFromQuery(combinedSceneQueryParams, projectId)
              .transact(xa)
              .unsafeToFuture()) { scenesAdded =>
            complete((StatusCodes.Created, scenesAdded))
          }
        }
      }
  }

  def updateProjectScenes(projectId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[Seq[UUID]]) { sceneIds =>
        if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
          complete(StatusCodes.RequestEntityTooLarge)
        }

        sceneIds.toList.toNel match {
          case Some(ids) => {
            complete {
              ProjectDao
                .replaceScenesInProject(ids, projectId)
                .transact(xa)
                .unsafeToFuture()
            }
          }
          case _ => complete(StatusCodes.BadRequest)
        }
      }
    }
  }

  def deleteProjectScenes(projectId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[Seq[UUID]]) { sceneIds =>
        if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
          complete(StatusCodes.RequestEntityTooLarge)
        }

        onSuccess(
          ProjectDao
            .deleteScenesFromProject(sceneIds.toList, projectId)
            .transact(xa)
            .unsafeToFuture()) { _ =>
          complete(StatusCodes.NoContent)
        }
      }
    }
  }

  def listProjectPermissions(projectId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao.query
        .ownedBy(user, projectId)
        .exists
        .transact(xa)
        .unsafeToFuture
    } {
      complete {
        ProjectDao
          .getPermissions(projectId)
          .transact(xa)
          .unsafeToFuture
      }
    }
  }

  def replaceProjectPermissions(projectId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao.query
        .ownedBy(user, projectId)
        .exists
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[List[ObjectAccessControlRule]]) { acrList =>
        complete {
          ProjectDao
            .replacePermissions(projectId, acrList)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def addProjectPermission(projectId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao.query
        .ownedBy(user, projectId)
        .exists
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[ObjectAccessControlRule]) { acr =>
        complete {
          ProjectDao
            .addPermission(projectId, acr)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def listUserProjectActions(projectId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      user.isSuperuser match {
        case true => complete(List("*"))
        case false =>
          onSuccess(
            ProjectDao
              .unsafeGetProjectById(projectId)
              .transact(xa)
              .unsafeToFuture
          ) { project =>
            project.owner == user.id match {
              case true => complete(List("*"))
              case false =>
                complete {
                  ProjectDao
                    .listUserActions(user, projectId)
                    .transact(xa)
                    .unsafeToFuture
                }
            }
          }
      }
    }
  }

  def deleteProjectPermissions(projectId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao.query
        .ownedBy(user, projectId)
        .exists
        .transact(xa)
        .unsafeToFuture
    } {
      complete {
        ProjectDao
          .deletePermissions(projectId)
          .transact(xa)
          .unsafeToFuture
      }
    }
  }

  def processShapefile(projectId: UUID,
                       tempFile: ScalaFile,
                       fileMetadata: FileInfo,
                       propsO: Option[Map[String, String]] = None): Route =
    authenticate { user =>
      {
        val unzipped = tempFile.unzip()
        val matches = unzipped.glob("*.shp")
        val prj = unzipped.glob("*.prj")
        (matches.hasNext, prj.hasNext) match {
          case (false, false) =>
            complete(
              StatusCodes.ClientError(400)(
                "Bad Request",
                "No .shp and .prj Files Found in Archive"))
          case (true, false) =>
            complete(
              StatusCodes.ClientError(400)("Bad Request",
                                           "No .prj File Found in Archive"))
          case (false, true) =>
            complete(
              StatusCodes.ClientError(400)("Bad Request",
                                           "No .shp File Found in Archive"))
          case (true, true) => {
            propsO match {
              case Some(props) =>
                processShapefileImport(matches, prj, props, user, projectId)
              case _ =>
                complete(StatusCodes.OK, processShapefileUpload(matches))
            }
          }
        }
      }
    }

  def processShapefileImport(matches: Iterator[ScalaFile],
                             prj: Iterator[ScalaFile],
                             props: Map[String, String],
                             user: User,
                             projectId: UUID): Route = {
    val shapefilePath = matches.next.toString
    val prjPath: String = prj.next.toString
    val projectionSource = scala.io.Source.fromFile(prjPath)

    val features = ShapeFileReader.readSimpleFeatures(shapefilePath)
    val projection = try projectionSource.mkString
    finally projectionSource.close()

    val featureAccumulationResult =
      Shapefile.accumulateFeatures(Annotation.fromSimpleFeatureWithProps)(
        List(),
        List(),
        features.toList,
        props,
        user.id,
        projection)
    featureAccumulationResult match {
      case Left(errorIndices) =>
        complete(
          StatusCodes.ClientError(400)(
            "Bad Request",
            s"Several features could not be translated to annotations. Indices: ${errorIndices}"
          )
        )
      case Right(annotationCreates) => {
        complete(
          StatusCodes.Created,
          (AnnotationDao.insertAnnotations(annotationCreates, projectId, user)
            map { (anns: List[Annotation]) =>
              anns map { _.toGeoJSONFeature }
            }).transact(xa).unsafeToFuture
        )
      }
    }
  }

  def processShapefileUpload(matches: Iterator[ScalaFile]): List[String] = {
    // shapefile should have same fields in the property table
    // so it is fine to use toList(0)
    ShapeFileReader
      .readSimpleFeatures(matches.next.toString)
      .toList(0)
      .toString
      .split("SimpleFeatureImpl")
      .filter(s => s != "" && s.contains(".Attribute: "))
      .map(_.split(".Attribute: ")(1).split("<")(0))
      .toList
  }
}
