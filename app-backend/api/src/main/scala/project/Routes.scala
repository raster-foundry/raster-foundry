package com.rasterfoundry.api.project

import com.rasterfoundry.akkautil.PaginationDirectives
import com.rasterfoundry.akkautil.{
  Authentication,
  CommonHandlers,
  UserErrorHandler
}
import com.rasterfoundry.api.scene._
import com.rasterfoundry.api.utils.Config
import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.common._
import com.rasterfoundry.common.color._
import com.rasterfoundry.common.{AWSBatch, RollbarNotifier}
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import cats.Applicative
import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor

import java.util.UUID

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

  implicit def contextShift: ContextShift[IO]
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
                            getProjectLayerSceneColorCorrectParams(
                              projectId,
                              layerId,
                              sceneId
                            )
                          } ~
                            put {
                              setProjectLayerSceneColorCorrectParams(
                                projectId,
                                layerId,
                                sceneId
                              )
                            }
                        }
                      } ~
                      pathPrefix("bulk-update-color-corrections") {
                        pathEndOrSingleSlash {
                          post {
                            setProjectLayerScenesColorCorrectParams(
                              projectId,
                              layerId
                            )
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
                              updateLayerAnnotation(projectId, layerId)
                            } ~
                            delete {
                              deleteLayerAnnotation(
                                projectId,
                                annotationId,
                                layerId
                              )
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
                            getLayerAnnotationGroup(
                              projectId,
                              layerId,
                              annotationGroupId
                            )
                          } ~
                            put {
                              updateLayerAnnotationGroup(
                                projectId,
                                layerId,
                                annotationGroupId
                              )
                            } ~
                            delete {
                              deleteLayerAnnotationGroup(
                                projectId,
                                layerId,
                                annotationGroupId
                              )
                            }
                        } ~
                          pathPrefix("summary") {
                            getLayerAnnotationGroupSummary(
                              projectId,
                              layerId,
                              annotationGroupId
                            )
                          } ~
                          pathPrefix("uploads") {
                            pathEndOrSingleSlash {
                              get {
                                listGeojsonUploads(
                                  projectId,
                                  layerId,
                                  annotationGroupId
                                )
                              } ~
                                post {
                                  createGeojsonUpload(
                                    projectId,
                                    layerId,
                                    annotationGroupId
                                  )
                                }
                            } ~
                              pathPrefix(JavaUUID) { uploadId =>
                                get {
                                  getGeojsonUpload(
                                    projectId,
                                    layerId,
                                    annotationGroupId,
                                    uploadId
                                  )
                                } ~
                                  put {
                                    updateGeojsonUpload(
                                      projectId,
                                      layerId,
                                      annotationGroupId,
                                      uploadId
                                    )
                                  } ~
                                  delete {
                                    deleteGeojsonUpload(
                                      projectId,
                                      layerId,
                                      annotationGroupId,
                                      uploadId
                                    )
                                  }
                              }
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
              pathPrefix(JavaUUID) { annotationId =>
                pathEndOrSingleSlash {
                  get {
                    getAnnotation(projectId, annotationId)
                  } ~
                    put {
                      updateAnnotation(projectId)
                    } ~
                    delete {
                      deleteAnnotation(projectId, annotationId)
                    }
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

  def listProjects: Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Projects, Action.Read, None), user) {
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
  }

  def createProject: Route = authenticate { case MembershipAndUser(_, user) =>
    val userProjectCount = ProjectDao.query
      .filter(fr"owner = ${user.id}")
      .count
      .transact(xa)
      .unsafeToFuture
    authorizeScopeLimit(userProjectCount, Domain.Projects, Action.Create, user) {
      entity(as[Project.Create]) { newProject =>
        onSuccess(
          ProjectDao
            .insertProject(newProject, user)
            .transact(xa)
            .unsafeToFuture
        ) { project =>
          complete(StatusCodes.Created, project)
        }
      }
    }
  }

  def getProject(projectId: UUID): Route = authenticateAllowAnonymous { user =>
    authorizeScope(ScopedAction(Domain.Projects, Action.Read, None), user) {
      (authorizeAsync(
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
          .map(_.toBoolean)
      ) | projectIsPublic(projectId)) {
        complete {
          ProjectDao.getProjectById(projectId).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def updateProject(projectId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Projects, Action.Update, None), user) {
      authorizeAuthResultAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[Project]) { updatedProject =>
          onSuccess(
            ProjectDao
              .updateProject(updatedProject, projectId)
              .transact(xa)
              .unsafeToFuture
          ) {
            completeSingleOrNotFound
          }
        }
      }
    }
  }

  def deleteProject(projectId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Projects, Action.Delete, None), user) {
      authorizeAuthResultAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Delete)
          .transact(xa)
          .unsafeToFuture
      } {
        onSuccess(
          ProjectDao.deleteProject(projectId).transact(xa).unsafeToFuture
        ) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def listLabels(projectId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Projects, Action.Read, None), user) {
      authorizeAuthResultAsync {
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
  }

  def acceptScene(projectId: UUID, sceneId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
      authorizeScope(
        ScopedAction(Domain.Projects, Action.AddScenes, None),
        user
      ) {
        authorizeAuthResultAsync {
          ProjectDao
            .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
            .transact(xa)
            .unsafeToFuture
        } {
          complete {
            val acceptSceneIO = for {
              project <- ProjectDao.unsafeGetProjectById(projectId)
              rowsAffected <- SceneToLayerDao
                .acceptScene(project.defaultLayerId, sceneId)
            } yield {
              rowsAffected
            }

            acceptSceneIO.transact(xa).unsafeToFuture
          }
        }
      }
  }

  def acceptScenes(projectId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Projects, Action.AddScenes, None), user) {
      authorizeAuthResultAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[List[UUID]]) { sceneIds =>
          if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
            complete(StatusCodes.PayloadTooLarge)
          }

          val acceptScenesIO = for {
            project <- ProjectDao.unsafeGetProjectById(projectId)
            rowsAffected <- SceneToLayerDao
              .acceptScenes(project.defaultLayerId, sceneIds)
          } yield {
            rowsAffected
          }

          onSuccess(acceptScenesIO.transact(xa).unsafeToFuture) { _ =>
            complete(StatusCodes.NoContent)
          }
        }
      }
    }
  }

  def listProjectScenes(projectId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Projects, Action.Read, None), user) {
      authorizeAuthResultAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.View)
          .transact(xa)
          .unsafeToFuture
      } {
        (withPagination & projectSceneQueryParameters) { (page, sceneParams) =>
          complete {
            val sceneListIO = for {
              project <- ProjectDao.unsafeGetProjectById(projectId)
              scenes <- ProjectLayerScenesDao.listLayerScenes(
                project.defaultLayerId,
                page,
                sceneParams
              )
            } yield scenes
            sceneListIO.transact(xa).unsafeToFuture
          }
        }
      }
    }
  }

  def listProjectDatasources(projectId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Datasources, Action.Read, None), user) {
      (projectQueryParameters) { projectQueryParams =>
        authorizeAsync {
          val authorized = for {
            authProject <- ProjectDao
              .authorized(user, ObjectType.Project, projectId, ActionType.View)
            authResult <- (authProject, projectQueryParams.analysisId) match {
              case (AuthFailure(), Some(analysisId: UUID)) =>
                ToolRunDao
                  .authorizeReferencedProject(user, analysisId, projectId)
              case (_, _) =>
                Applicative[ConnectionIO].pure(authProject.toBoolean)
            }
          } yield authResult
          authorized.transact(xa).unsafeToFuture
        } {
          complete {
            val datasourcesIO = for {
              project <- ProjectDao.unsafeGetProjectById(projectId)
              datasources <- ProjectLayerDatasourcesDao
                .listProjectLayerDatasources(project.defaultLayerId)
            } yield datasources
            datasourcesIO
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }
  }

  /** Set the manually defined z-ordering for scenes within a given project */
  def setProjectSceneOrder(projectId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(
      ScopedAction(Domain.Projects, Action.ColorCorrect, None),
      user
    ) {
      authorizeAuthResultAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[List[UUID]]) { sceneIds =>
          if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
            complete(StatusCodes.PayloadTooLarge)
          }

          val setOrderIO = for {
            project <- ProjectDao.unsafeGetProjectById(projectId)
            updatedOrder <- SceneToLayerDao
              .setManualOrder(project.defaultLayerId, sceneIds)
          } yield {
            updatedOrder
          }

          onSuccess(setOrderIO.transact(xa).unsafeToFuture) { _ =>
            complete(StatusCodes.NoContent)
          }
        }
      }
    }
  }

  /** Get the color correction paramters for a project/scene pairing */
  def getProjectSceneColorCorrectParams(projectId: UUID, sceneId: UUID) =
    authenticate { case MembershipAndUser(_, user) =>
      authorizeScope(
        ScopedAction(Domain.Projects, Action.ColorCorrect, None),
        user
      ) {
        authorizeAuthResultAsync {
          ProjectDao
            .authorized(user, ObjectType.Project, projectId, ActionType.View)
            .transact(xa)
            .unsafeToFuture
        } {
          complete {
            val getColorCorrectParamsIO = for {
              project <- ProjectDao.unsafeGetProjectById(projectId)
              params <- SceneToLayerDao.getColorCorrectParams(
                project.defaultLayerId,
                sceneId
              )
            } yield {
              params
            }

            getColorCorrectParamsIO.transact(xa).unsafeToFuture
          }
        }
      }
    }

  /** Set color correction parameters for a project/scene pairing */
  def setProjectSceneColorCorrectParams(projectId: UUID, sceneId: UUID) =
    authenticate { case MembershipAndUser(_, user) =>
      authorizeScope(
        ScopedAction(Domain.Projects, Action.ColorCorrect, None),
        user
      ) {
        authorizeAuthResultAsync {
          ProjectDao
            .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[ColorCorrect.Params]) { ccParams =>
            val setColorCorrectParamsIO = for {
              project <- ProjectDao.unsafeGetProjectById(projectId)
              stl <- SceneToLayerDao.setColorCorrectParams(
                project.defaultLayerId,
                sceneId,
                ccParams
              )
            } yield {
              stl
            }

            onSuccess(setColorCorrectParamsIO.transact(xa).unsafeToFuture) {
              _ =>
                complete(StatusCodes.NoContent)
            }
          }
        }
      }
    }

  def setProjectColorMode(projectId: UUID) = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(
      ScopedAction(Domain.Projects, Action.ColorCorrect, None),
      user
    ) {
      authorizeAuthResultAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[ProjectColorModeParams]) { colorBands =>
          val setProjectColorBandsIO = for {
            project <- ProjectDao.unsafeGetProjectById(projectId)
            rowsAffected <- SceneToLayerDao
              .setProjectLayerColorBands(project.defaultLayerId, colorBands)
          } yield {
            rowsAffected
          }

          onSuccess(setProjectColorBandsIO.transact(xa).unsafeToFuture) { _ =>
            complete(StatusCodes.NoContent)
          }
        }
      }
    }
  }

  /** Set color correction parameters for a list of scenes */
  def setProjectScenesColorCorrectParams(projectId: UUID) = authenticate { case MembershipAndUser(_, user) =>
      authorizeScope(
        ScopedAction(Domain.Projects, Action.ColorCorrect, None),
        user
      ) {
        authorizeAuthResultAsync {
          ProjectDao
            .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[BatchParams]) { params =>
            val setColorCorrectParamsBatchIO = for {
              project <- ProjectDao.unsafeGetProjectById(projectId)
              stl <- SceneToLayerDao
                .setColorCorrectParamsBatch(project.defaultLayerId, params)
            } yield {
              stl
            }

            onSuccess(setColorCorrectParamsBatchIO.transact(xa).unsafeToFuture) {
              _ =>
                complete(StatusCodes.NoContent)
            }
          }
        }
      }
  }

  /** Get the information which defines mosaicing behavior for each scene in a given project */
  def getProjectMosaicDefinition(projectId: UUID) = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Projects, Action.Read, None), user) {
      authorizeAuthResultAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.View)
          .transact(xa)
          .unsafeToFuture
      } {
        rejectEmptyResponse {
          complete {
            val getMosaicDefinitionIO = for {
              project <- ProjectDao.unsafeGetProjectById(projectId)
              result <- SceneToLayerDao
                .getMosaicDefinition(project.defaultLayerId)
            } yield {
              result
            }

            getMosaicDefinitionIO
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }
  }

  def addProjectScenes(projectId: UUID, layerIdO: Option[UUID] = None): Route =
    authenticate { case MembershipAndUser(_, user) =>
      authorizeScope(
        ScopedAction(Domain.Projects, Action.AddScenes, None),
        user
      ) {
        authorizeAuthResultAsync {
          ProjectDao
            .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[NonEmptyList[UUID]]) { sceneIds =>
            if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
              complete(StatusCodes.PayloadTooLarge)
            }

            val scenesAdded = for {
              project <- ProjectDao.unsafeGetProjectById(projectId)
              layerId = ProjectDao.getProjectLayerId(layerIdO, project)
              addedScenes <- ProjectDao.addScenesToProject(
                sceneIds,
                projectId,
                layerId,
                true
              )
            } yield addedScenes

            complete {
              scenesAdded.transact(xa).unsafeToFuture
            }
          }
        }
      }
    }

  def updateProjectScenes(
      projectId: UUID,
      layerIdO: Option[UUID] = None
  ): Route =
    authenticate { case MembershipAndUser(_, user) =>
      authorizeScope(
        ScopedAction(Domain.Projects, Action.EditScenes, None),
        user
      ) {
        authorizeAuthResultAsync {
          ProjectDao
            .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[Seq[UUID]]) { sceneIds =>
            if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
              complete(StatusCodes.PayloadTooLarge)
            }

            sceneIds.toList.toNel match {
              case Some(ids) =>
                val replaceIO = for {
                  project <- ProjectDao.unsafeGetProjectById(projectId)
                  layerId = ProjectDao.getProjectLayerId(layerIdO, project)
                  replacement <- ProjectDao.replaceScenesInProject(
                    ids,
                    projectId,
                    layerId
                  )
                } yield replacement

                complete {
                  replaceIO.transact(xa).unsafeToFuture()
                }
              case _ => complete(StatusCodes.BadRequest)
            }
          }
        }
      }
    }

  def deleteProjectScenes(
      projectId: UUID,
      layerIdO: Option[UUID] = None
  ): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Projects, Action.EditScenes, None), user) {
      authorizeAuthResultAsync {
        ProjectDao
          .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[Seq[UUID]]) { sceneIds =>
          if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
            complete(StatusCodes.PayloadTooLarge)
          }

          val deleteIO = for {
            project <- ProjectDao.unsafeGetProjectById(projectId)
            layerId = ProjectDao.getProjectLayerId(layerIdO, project)
            deletion <- ProjectDao
              .deleteScenesFromProject(sceneIds.toList, projectId, layerId)
          } yield deletion
          onSuccess(deleteIO.transact(xa).unsafeToFuture) { _ =>
            complete(StatusCodes.NoContent)
          }
        }
      }
    }
  }

  def listProjectPermissions(projectId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(
      ScopedAction(Domain.Projects, Action.ReadPermissions, None),
      user
    ) {
      authorizeAuthResultAsync {
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
  }

  def replaceProjectPermissions(projectId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Projects, Action.Share, None), user) {
      entity(as[List[ObjectAccessControlRule]]) { acrList =>
        authorizeAsync {
          (
            ProjectDao.authorized(
              user,
              ObjectType.Project,
              projectId,
              ActionType.Edit
            ) map {
              _.toBoolean
            },
            acrList traverse { acr =>
              ProjectDao.isValidPermission(acr, user)
            } map {
              _.foldLeft(true)(_ && _)
            } map {
              case true =>
                ProjectDao.isReplaceWithinScopedLimit(
                  Domain.Projects,
                  user,
                  acrList
                )
              case _ => false
            }
          ).tupled
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
  }

  def addProjectPermission(projectId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    val shareCount =
      ProjectDao.getShareCount(projectId, user.id).transact(xa).unsafeToFuture
    authorizeScopeLimit(
      shareCount,
      Domain.Projects,
      Action.Share,
      user
    ) {
      entity(as[ObjectAccessControlRule]) { acr =>
        authorizeAsync {
          (
            ProjectDao.authorized(
              user,
              ObjectType.Project,
              projectId,
              ActionType.Edit
            ) map {
              _.toBoolean
            },
            ProjectDao.isValidPermission(acr, user)
          ).tupled
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
  }

  def listUserProjectActions(projectId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(
      ScopedAction(Domain.Projects, Action.ReadPermissions, None),
      user
    ) {
      authorizeAuthResultAsync {
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
  }

  def deleteProjectPermissions(projectId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Projects, Action.Share, None), user) {
      authorizeAuthResultAsync {
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
  }
}
