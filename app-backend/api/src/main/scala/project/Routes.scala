package com.rasterfoundry.api.project

import java.util.UUID

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives._
import better.files.{File => ScalaFile}
import cats.data.NonEmptyList
import cats.effect.IO
import cats.implicits._
import com.rasterfoundry.api.scene._
import com.rasterfoundry.api.utils.Config
import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.common.utils.Shapefile
import com.rasterfoundry.common.{AWSBatch, RollbarNotifier}
import com.rasterfoundry.akkautil.{
  Authentication,
  CommonHandlers,
  UserErrorHandler
}
import com.rasterfoundry.database._
import com.rasterfoundry.database.filter.Filterables._
import com.rasterfoundry.common.datamodel.{Annotation, _}
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import geotrellis.shapefile.ShapeFileReader
import io.circe.generic.JsonCodec

import scala.util.Success

@JsonCodec
final case class BulkAcceptParams(sceneIds: List[UUID])

trait ProjectRoutes
    extends Authentication
    with Config
    with Directives
    with QueryParametersCommon
    with SceneQueryParameterDirective
    with ProjectSceneQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with AWSBatch
    with UserErrorHandler
    with RollbarNotifier
    with LazyLogging
    with ProjectAnnotationRoutes
    with ProjectLayerRoutes
    with ProjectLayerAnnotationRoutes
    with ProjectAuthorizationDirectives {

  val xa: Transactor[IO]

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
            } ~ pathPrefix("stats") {
              pathEndOrSingleSlash {
                get {
                  getProjectLayerSceneCounts(projectId)
                }
              }
            } ~
              pathPrefix(JavaUUID) { layerId =>
                pathEndOrSingleSlash {
                  get {
                    getProjectLayer(projectId, layerId)
                  } ~
                    put {
                      updateProjectLayer(projectId, layerId)
                    } ~
                    delete {
                      deleteProjectLayer(projectId, layerId)
                    }
                } ~
                  pathPrefix("color-mode") {
                    pathEndOrSingleSlash {
                      post {
                        setProjectLayerColorMode(projectId, layerId)
                      }
                    }
                  } ~
                  pathPrefix("mosaic") {
                    pathEndOrSingleSlash {
                      get {
                        getProjectLayerMosaicDefinition(projectId, layerId)
                      }
                    } ~
                      pathPrefix(JavaUUID) { sceneId =>
                        pathEndOrSingleSlash {
                          get {
                            getProjectLayerSceneColorCorrectParams(projectId,
                                                                   layerId,
                                                                   sceneId)
                          } ~
                            put {
                              setProjectLayerSceneColorCorrectParams(projectId,
                                                                     layerId,
                                                                     sceneId)
                            }
                        }
                      } ~
                      pathPrefix("bulk-update-color-corrections") {
                        pathEndOrSingleSlash {
                          post {
                            setProjectLayerScenesColorCorrectParams(projectId,
                                                                    layerId)
                          }
                        }
                      }
                  } ~
                  pathPrefix("order") {
                    pathEndOrSingleSlash {
                      put {
                        setProjectLayerSceneOrder(projectId, layerId)
                      }
                    }
                  } ~
                  pathPrefix("labels") {
                    pathEndOrSingleSlash {
                      get {
                        listLayerLabels(projectId, layerId)
                      }
                    }
                  } ~
                  pathPrefix("annotations") {
                    pathEndOrSingleSlash {
                      get {
                        listLayerAnnotations(projectId, layerId)
                      } ~
                        post {
                          createLayerAnnotation(projectId, layerId)
                        } ~
                        delete {
                          deleteLayerAnnotations(projectId, layerId)
                        }
                    } ~
                      pathPrefix(JavaUUID) { annotationId =>
                        pathEndOrSingleSlash {
                          get {
                            getLayerAnnotation(projectId, annotationId, layerId)
                          } ~
                            put {
                              updateLayerAnnotation(projectId,
                                                    annotationId,
                                                    layerId)
                            } ~
                            delete {
                              deleteLayerAnnotation(projectId,
                                                    annotationId,
                                                    layerId)
                            }
                        }
                      } ~
                      pathPrefix("shapefile") {
                        pathEndOrSingleSlash {
                          get {
                            exportLayerAnnotationShapefile(projectId, layerId)
                          } ~
                            post {
                              authenticate { user =>
                                val tempFile = ScalaFile.newTemporaryFile()
                                tempFile.deleteOnExit()
                                val response =
                                  storeUploadedFile("name",
                                                    (_) => tempFile.toJava) {
                                    (m, _) =>
                                      processShapefile(projectId,
                                                       tempFile,
                                                       m,
                                                       None,
                                                       Some(layerId))
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
                                                         Some(fields),
                                                         Some(layerId))
                                    }
                                  tempFile.delete()
                                  response
                                }
                              }
                            }
                          }
                      }
                  } ~
                  pathPrefix("annotation-groups") {
                    pathEndOrSingleSlash {
                      get {
                        listLayerAnnotationGroups(projectId, layerId)
                      } ~
                        post {
                          createLayerAnnotationGroup(projectId, layerId)
                        }
                    } ~
                      pathPrefix(JavaUUID) { annotationGroupId =>
                        pathEndOrSingleSlash {
                          get {
                            getLayerAnnotationGroup(projectId,
                                                    layerId,
                                                    annotationGroupId)
                          } ~
                            put {
                              updateLayerAnnotationGroup(projectId,
                                                         layerId,
                                                         annotationGroupId)
                            } ~
                            delete {
                              deleteLayerAnnotationGroup(projectId,
                                                         layerId,
                                                         annotationGroupId)
                            }
                        } ~
                          pathPrefix("summary") {
                            getLayerAnnotationGroupSummary(projectId,
                                                           layerId,
                                                           annotationGroupId)
                          }
                      }
                  } ~
                  pathPrefix("scenes") {
                    pathEndOrSingleSlash {
                      get {
                        listLayerScenes(projectId, layerId)
                      } ~
                        post {
                          addProjectScenes(projectId, Some(layerId))
                        } ~
                        put {
                          updateProjectScenes(projectId, Some(layerId))
                        } ~
                        delete {
                          deleteProjectScenes(projectId, Some(layerId))
                        }
                    }
                  } ~
                  pathPrefix("datasources") {
                    pathEndOrSingleSlash {
                      get {
                        listLayerDatasources(projectId, layerId)
                      }
                    }
                  }
              }
          } ~
          pathPrefix("analyses") {
            pathEndOrSingleSlash {
              get {
                listProjectAnalyses(projectId)
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
                              processShapefile(projectId,
                                               tempFile,
                                               m,
                                               None,
                                               None)
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
                                                 Some(fields),
                                                 None)
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

  def addProjectScenes(projectId: UUID, layerId: Option[UUID] = None): Route =
    authenticate { user =>
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
            ProjectDao.addScenesToProject(sceneIds, projectId, true, layerId)

          complete { scenesAdded.transact(xa).unsafeToFuture }
        }
      }
    }

  def updateProjectScenes(projectId: UUID,
                          layerId: Option[UUID] = None): Route =
    authenticate { user =>
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
                  .replaceScenesInProject(ids, projectId, layerId)
                  .transact(xa)
                  .unsafeToFuture()
              }
            }
            case _ => complete(StatusCodes.BadRequest)
          }
        }
      }
    }

  def deleteProjectScenes(projectId: UUID,
                          layerId: Option[UUID] = None): Route = authenticate {
    user =>
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
              .deleteScenesFromProject(sceneIds.toList, projectId, layerId)
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
                       propsO: Option[Map[String, String]] = None,
                       projectLayerIdO: Option[UUID]): Route =
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
                processShapefileImport(matches,
                                       prj,
                                       props,
                                       user,
                                       projectId,
                                       projectLayerIdO)
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
                             projectId: UUID,
                             projectLayerIdO: Option[UUID]): Route = {
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
          (AnnotationDao.insertAnnotations(annotationCreates,
                                           projectId,
                                           user,
                                           projectLayerIdO)
            map { (anns: List[Annotation]) =>
              anns map { _.toGeoJSONFeature }
            }).transact(xa).unsafeToFuture
        )
      }
    }
  }

  def listProjectAnalyses(projectId: UUID): Route =
    extractTokenHeader { tokenO =>
      extractMapTokenParam { mapTokenO =>
        (projectAuthFromMapTokenO(mapTokenO, projectId) | projectAuthFromTokenO(
          tokenO,
          projectId) | projectIsPublic(projectId)) {
          withPagination { page =>
            complete {
              ToolRunDao
                .listAnalysesWithRelated(
                  None,
                  page,
                  projectId
                )
                .transact(xa)
                .unsafeToFuture
            }
          }
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
