package com.rasterfoundry.api.project

import java.util.{Calendar, UUID}

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileInfo
import better.files.{File => ScalaFile}
import cats.data.NonEmptyList
import cats.effect.IO
import cats.implicits._
import com.amazonaws.services.s3.AmazonS3URI
import com.rasterfoundry.api.scene._
import com.rasterfoundry.api.utils.Config
import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.common.S3
import com.rasterfoundry.common.utils.Shapefile
import com.rasterfoundry.common.{AWSBatch, RollbarNotifier}
import com.rasterfoundry.akkautil.{
  Authentication,
  CommonHandlers,
  UserErrorHandler
}
import com.rasterfoundry.database._
import com.rasterfoundry.database.filter.Filterables._
import com.rasterfoundry.datamodel.GeoJsonCodec._
import com.rasterfoundry.datamodel.{Annotation, _}
import com.lonelyplanet.akka.http.extensions.{PageRequest, PaginationDirectives}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import geotrellis.shapefile.ShapeFileReader
import io.circe.generic.JsonCodec

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
    with LazyLogging {

  val xa: Transactor[IO]

  val BULK_OPERATION_MAX_LIMIT = 100

  val projectRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get {
        listProjects
      } ~
        post {
          createProject
        }
    } ~
      pathPrefix(JavaUUID) { projectId =>
        pathEndOrSingleSlash {
          get {
            getProject(projectId)
          } ~
            put {
              updateProject(projectId)
            } ~
            delete {
              deleteProject(projectId)
            }
        } ~
          pathPrefix("layers") {
            pathEndOrSingleSlash {
              post {
                createProjectLayer(projectId)
              } ~
                get {
                  listProjectLayers(projectId)
                }
            } ~
              pathPrefix(JavaUUID) { layerId =>
                get {
                  getProjectLayer(projectId, layerId)
                } ~
                  put {
                    updateProjectLayer(projectId, layerId)
                  } ~
                  delete {
                    deleteProjectLayer(projectId, layerId)
                  }
              }
          } ~
          pathPrefix("project-color-mode") {
            pathEndOrSingleSlash {
              post {
                setProjectColorMode(projectId)
              }
            }
          } ~
          pathPrefix("labels") {
            pathEndOrSingleSlash {
              get {
                listLabels(projectId)
              }
            }
          } ~
          pathPrefix("annotation-groups") {
            pathEndOrSingleSlash {
              get {
                listAnnotationGroups(projectId)
              } ~
                post {
                  createAnnotationGroup(projectId)
                }
            } ~
              pathPrefix(JavaUUID) { annotationGroupId =>
                pathEndOrSingleSlash {
                  get {
                    getAnnotationGroup(projectId, annotationGroupId)
                  } ~
                    put {
                      updateAnnotationGroup(projectId, annotationGroupId)
                    } ~
                    delete {
                      deleteAnnotationGroup(projectId, annotationGroupId)
                    }
                } ~
                  pathPrefix("summary") {
                    getAnnotationGroupSummary(projectId, annotationGroupId)
                  }
              }
          } ~
          pathPrefix("annotations") {
            pathEndOrSingleSlash {
              get {
                listAnnotations(projectId)
              } ~
                post {
                  createAnnotation(projectId)
                } ~
                delete {
                  deleteProjectAnnotations(projectId)
                }
            } ~
              pathPrefix("shapefile") {
                pathEndOrSingleSlash {
                  get {
                    exportAnnotationShapefile(projectId)
                  } ~
                    post {
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
                } ~
                  pathPrefix("import") {
                    pathEndOrSingleSlash {
                      (post & formFieldMap) { fields =>
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
              } ~
              pathPrefix(JavaUUID) { annotationId =>
                pathEndOrSingleSlash {
                  get {
                    getAnnotation(projectId, annotationId)
                  } ~
                    put {
                      updateAnnotation(projectId, annotationId)
                    } ~
                    delete {
                      deleteAnnotation(projectId, annotationId)
                    }
                }
              }
          } ~
          pathPrefix("areas-of-interest") {
            pathEndOrSingleSlash {
              get {
                listAOIs(projectId)
              } ~
                post {
                  createAOI(projectId)
                }
            }
          } ~
          pathPrefix("datasources") {
            pathEndOrSingleSlash {
              get {
                listProjectDatasources(projectId)
              }
            }
          } ~
          pathPrefix("scenes") {
            pathEndOrSingleSlash {
              get {
                listProjectScenes(projectId)
              } ~
                post {
                  addProjectScenes(projectId)
                } ~
                put {
                  updateProjectScenes(projectId)
                } ~
                delete {
                  deleteProjectScenes(projectId)
                }
            } ~
              pathPrefix("bulk-add-from-query") {
                pathEndOrSingleSlash {
                  post {
                    addProjectScenesFromQueryParams(projectId)
                  }
                }
              } ~
              pathPrefix("accept") {
                post {
                  acceptScenes(projectId)
                }
              } ~
              pathPrefix(JavaUUID) { sceneId =>
                pathPrefix("accept") {
                  post {
                    acceptScene(projectId, sceneId)
                  }
                }
              }
          } ~
          pathPrefix("mosaic") {
            pathEndOrSingleSlash {
              get {
                getProjectMosaicDefinition(projectId)
              }
            } ~
              pathPrefix(JavaUUID) { sceneId =>
                get {
                  getProjectSceneColorCorrectParams(projectId, sceneId)
                } ~
                  put {
                    setProjectSceneColorCorrectParams(projectId, sceneId)
                  }
              } ~
              pathPrefix("bulk-update-color-corrections") {
                pathEndOrSingleSlash {
                  post {
                    setProjectScenesColorCorrectParams(projectId)
                  }
                }
              }
          } ~
          pathPrefix("order") {
            pathEndOrSingleSlash {
              put {
                setProjectSceneOrder(projectId)
              }
            }
          } ~
          pathPrefix("permissions") {
            pathEndOrSingleSlash {
              put {
                replaceProjectPermissions(projectId)
              } ~
                post {
                  addProjectPermission(projectId)
                } ~
                get {
                  listProjectPermissions(projectId)
                } ~
                delete {
                  deleteProjectPermissions(projectId)
                }
            }
          } ~
          pathPrefix("actions") {
            pathEndOrSingleSlash {
              get {
                listUserProjectActions(projectId)
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
          (projectQueryParameters) { projectQueryParams =>
            authorizeAsync {
              val authorized = for {
                authProject <- ProjectDao.authorized(user,
                                                     ObjectType.Project,
                                                     projectId,
                                                     ActionType.View)
                authResult <- (authProject, projectQueryParams.analysisId) match {
                  case (false, Some(analysisId: UUID)) =>
                    ToolRunDao
                      .authorizeReferencedProject(user, analysisId, projectId)
                  case (_, _) => authProject.pure[ConnectionIO]
                }
              } yield authResult
              authorized.transact(xa).unsafeToFuture
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

  def getAnnotationGroupSummary(projectId: UUID,
                                annotationGroupId: UUID): Route = authenticate {
    user =>
      authorizeAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.View)
          .transact(xa)
          .unsafeToFuture
      } {
        complete {
          AnnotationGroupDao
            .getAnnotationGroupSummary(annotationGroupId)
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
              .page(page)
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
        onSuccess(
          AnnotationDao
            .insertAnnotations(annotationsCreate.toList, projectId, user)
            .transact(xa)
            .unsafeToFuture
            .map { annotations: List[Annotation] =>
              fromSeqToFeatureCollection[Annotation, Annotation.GeoJSON](
                annotations)
            }
        ) { createdAnnotation =>
          complete((StatusCodes.Created, createdAnnotation))
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
          val s3Uri: AmazonS3URI = new AmazonS3URI(
            user.getDefaultAnnotationShapefileSource(dataBucket))
          val s3Client = S3()
          s3Client
            .putObject(dataBucket, s3Uri.getKey, zipfile.toJava)
            .setExpirationTime(cal.getTime)
          zipfile.delete(true)
          complete(s3Client.getSignedUrl(dataBucket, s3Uri.getKey).toString())
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
          val acceptSceneIO = for {
            project <- ProjectDao.unsafeGetProjectById(projectId)
            _ <- SceneToProjectDao.acceptScene(projectId, sceneId)
            rowsAffected <- SceneToLayerDao.acceptScene(project.defaultLayerId,
                                                        sceneId)
          } yield { rowsAffected }

          acceptSceneIO.transact(xa).unsafeToFuture
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

        val acceptScenesIO = for {
          project <- ProjectDao.unsafeGetProjectById(projectId)
          _ <- SceneToProjectDao.acceptScenes(projectId, sceneIds)
          rowsAffected <- SceneToLayerDao.acceptScenes(project.defaultLayerId,
                                                       sceneIds)
        } yield { rowsAffected }

        onSuccess(acceptScenesIO.transact(xa).unsafeToFuture) { updatedOrder =>
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
    (projectQueryParameters) { projectQueryParams =>
      authorizeAsync {
        val authorized = for {
          authProject <- ProjectDao.authorized(user,
                                               ObjectType.Project,
                                               projectId,
                                               ActionType.View)
          authResult <- (authProject, projectQueryParams.analysisId) match {
            case (false, Some(analysisId: UUID)) =>
              ToolRunDao
                .authorizeReferencedProject(user, analysisId, projectId)
            case (_, _) => authProject.pure[ConnectionIO]
          }
        } yield authResult
        authorized.transact(xa).unsafeToFuture
      } {
        complete {
          ProjectDatasourcesDao
            .listProjectDatasources(projectId)
            .transact(xa)
            .unsafeToFuture
        }
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

        val setOrderIO = for {
          project <- ProjectDao.unsafeGetProjectById(projectId)
          _ <- SceneToProjectDao.setManualOrder(projectId, sceneIds)
          updatedOrder <- SceneToLayerDao.setManualOrder(project.defaultLayerId,
                                                         sceneIds)
        } yield { updatedOrder }

        onSuccess(setOrderIO.transact(xa).unsafeToFuture) { updatedOrder =>
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
          val getColorCorrectParamsIO = for {
            project <- ProjectDao.unsafeGetProjectById(projectId)
            _ <- SceneToProjectDao.getColorCorrectParams(projectId, sceneId)
            params <- SceneToLayerDao.getColorCorrectParams(
              project.defaultLayerId,
              sceneId)
          } yield { params }

          getColorCorrectParamsIO.transact(xa).unsafeToFuture
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
          val setColorCorrectParamsIO = for {
            project <- ProjectDao.unsafeGetProjectById(projectId)
            _ <- SceneToProjectDao.setColorCorrectParams(projectId,
                                                         sceneId,
                                                         ccParams)
            stl <- SceneToLayerDao.setColorCorrectParams(project.defaultLayerId,
                                                         sceneId,
                                                         ccParams)
          } yield { stl }

          onSuccess(setColorCorrectParamsIO.transact(xa).unsafeToFuture) {
            stl =>
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
        val setProjectColorBandsIO = for {
          project <- ProjectDao.unsafeGetProjectById(projectId)
          _ <- SceneToProjectDao.setProjectColorBands(projectId, colorBands)
          rowsAffected <- SceneToLayerDao
            .setProjectLayerColorBands(project.defaultLayerId, colorBands)
        } yield { rowsAffected }

        onSuccess(setProjectColorBandsIO.transact(xa).unsafeToFuture) { _ =>
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
          val setColorCorrectParamsBatchIO = for {
            project <- ProjectDao.unsafeGetProjectById(projectId)
            _ <- SceneToProjectDao.setColorCorrectParamsBatch(projectId, params)
            stl <- SceneToLayerDao
              .setColorCorrectParamsBatch(project.defaultLayerId, params)
          } yield { stl }

          onSuccess(setColorCorrectParamsBatchIO.transact(xa).unsafeToFuture) {
            scenesToProject =>
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
          val getMosaicDefinitionIO = for {
            project <- ProjectDao.unsafeGetProjectById(projectId)
            _ <- SceneToProjectDao
              .getMosaicDefinition(projectId)
              .compile
              .to[List]
            result <- SceneToLayerDao
              .getMosaicDefinition(project.defaultLayerId)
              .compile
              .to[List]
          } yield { result }

          getMosaicDefinitionIO
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
          ProjectDao.addScenesToProject(sceneIds, projectId, true, None)

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
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
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
    entity(as[List[ObjectAccessControlRule]]) { acrList =>
      authorizeAsync {
        (ProjectDao.authorized(user,
                               ObjectType.Project,
                               projectId,
                               ActionType.Edit),
         acrList traverse { acr =>
           ProjectDao.isValidPermission(acr, user)
         } map { _.foldLeft(true)(_ && _) }).tupled
          .map({ authTup =>
            authTup._1 && authTup._2
          })
          .transact(xa)
          .unsafeToFuture
      } {
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
    entity(as[ObjectAccessControlRule]) { acr =>
      authorizeAsync {
        (ProjectDao.authorized(user,
                               ObjectType.Project,
                               projectId,
                               ActionType.Edit),
         ProjectDao.isValidPermission(acr, user)).tupled
          .map({ authTup =>
            authTup._1 && authTup._2
          })
          .transact(xa)
          .unsafeToFuture
      } {
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
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
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

  def createProjectLayer(projectId: UUID): Route = authenticate { user =>
    entity(as[ProjectLayer.Create]) { newProjectLayer =>
      authorizeAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
      } {
        onSuccess(
          ProjectLayerDao
            .insertProjectLayer(newProjectLayer.toProjectLayer)
            .transact(xa)
            .unsafeToFuture) { projectLayer =>
          complete(StatusCodes.Created, projectLayer)
        }
      }
    }
  }

  def listProjectLayers(projectId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ProjectDao
        .authorized(user, ObjectType.Project, projectId, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      complete {
        ProjectLayerDao
          .listProjectLayersForProject(projectId)
          .transact(xa)
          .unsafeToFuture
      }
    }
  }

  def getProjectLayer(projectId: UUID, layerId: UUID): Route = authenticate {
    user =>
      authorizeAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.View)
          .transact(xa)
          .unsafeToFuture
      } {
        rejectEmptyResponse {
          complete {
            ProjectLayerDao
              .getProjectLayer(projectId, layerId, user)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
  }

  def updateProjectLayer(projectId: UUID, layerId: UUID): Route = authenticate {
    user =>
      authorizeAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[ProjectLayer]) { updatedProjectLayer =>
          onSuccess(
            ProjectLayerDao
              .updateProjectLayer(updatedProjectLayer, layerId)
              .transact(xa)
              .unsafeToFuture) {
            completeSingleOrNotFound
          }
        }
      }
  }

  def deleteProjectLayer(projectId: UUID, layerId: UUID): Route = authenticate {
    user =>
      authorizeAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
      } {
        rejectEmptyResponse {
          complete {
            ProjectLayerDao
              .deleteProjectLayer(layerId)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
  }
}
