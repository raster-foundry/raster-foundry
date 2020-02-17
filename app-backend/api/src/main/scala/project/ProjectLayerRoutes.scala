package com.rasterfoundry.api.project

import com.rasterfoundry.akkautil.PaginationDirectives
import com.rasterfoundry.akkautil.{Authentication, CommonHandlers}
import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.common._
import com.rasterfoundry.common.color._
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import cats.Applicative
import cats.effect._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}

import java.util.UUID

trait ProjectLayerRoutes
    extends Authentication
    with CommonHandlers
    with PaginationDirectives
    with QueryParametersCommon
    with ProjectSceneQueryParameterDirective
    with ProjectAuthorizationDirectives {

  implicit val xa: Transactor[IO]

  val BULK_OPERATION_MAX_LIMIT = 100

  def createProjectLayer(projectId: UUID): Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Projects, Action.Create, None), user) {
      entity(as[ProjectLayer.Create]) { newProjectLayer =>
        authorizeAuthResultAsync {
          ProjectDao
            .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
            .transact(xa)
            .unsafeToFuture
        } {
          onSuccess(
            ProjectLayerDao
              .insertProjectLayer(newProjectLayer.toProjectLayer)
              .transact(xa)
              .unsafeToFuture
          ) { projectLayer =>
            complete(StatusCodes.Created, projectLayer)
          }
        }
      }
    }
  }

  def listProjectLayers(projectId: UUID): Route = extractTokenHeader { tokenO =>
    extractMapTokenParam { mapTokenO =>
      (projectAuthFromMapTokenO(mapTokenO, projectId) |
        projectAuthFromTokenO(
          tokenO,
          projectId,
          None,
          ScopedAction(Domain.Projects, Action.Read, None)
        ) | projectIsPublic(projectId)) {
        (withPagination) { (page) =>
          complete {
            ProjectLayerDao
              .listProjectLayersForProject(page, projectId)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }

    }
  }

  def getProjectLayer(projectId: UUID, layerId: UUID): Route = authenticate {
    user =>
      authorizeScope(ScopedAction(Domain.Projects, Action.Read, None), user) {
        authorizeAuthResultAsync {
          ProjectDao
            .authorized(user, ObjectType.Project, projectId, ActionType.View)
            .transact(xa)
            .unsafeToFuture
        } {
          rejectEmptyResponse {
            complete {
              ProjectLayerDao
                .getProjectLayer(projectId, layerId)
                .transact(xa)
                .unsafeToFuture
            }
          }
        }
      }
  }

  def updateProjectLayer(projectId: UUID, layerId: UUID): Route = authenticate {
    user =>
      authorizeScope(ScopedAction(Domain.Projects, Action.Update, None), user) {
        authorizeAsync {
          ProjectDao
            .authProjectLayerExist(projectId, layerId, user, ActionType.Edit)
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[ProjectLayer]) { updatedProjectLayer =>
            onSuccess(
              ProjectLayerDao
                .updateProjectLayer(updatedProjectLayer, layerId)
                .transact(xa)
                .unsafeToFuture
            ) {
              completeSingleOrNotFound
            }
          }
        }
      }
  }

  def deleteProjectLayer(projectId: UUID, layerId: UUID): Route = authenticate {
    user =>
      authorizeScope(ScopedAction(Domain.Projects, Action.Delete, None), user) {
        authorizeAsync {
          ProjectDao
            .authProjectLayerExist(projectId, layerId, user, ActionType.Edit)
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

  def splitProjectLayer(projectId: UUID, layerId: UUID): Route =
    authenticate { user =>
      authorizeScope(ScopedAction(Domain.Projects, Action.Create, None), user) {
        authorizeAsync {
          ProjectDao
            .authProjectLayerExist(projectId, layerId, user, ActionType.View)
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[SplitOptions]) { splitOptions =>
            onSuccess(
              ProjectLayerDao
                .splitProjectLayer(projectId, layerId, splitOptions)
                .transact(xa)
                .unsafeToFuture
            ) { splitLayers =>
              complete(StatusCodes.Created, splitLayers)
            }
          }
        }
      }
    }

  def getProjectLayerMosaicDefinition(projectId: UUID, layerId: UUID): Route =
    authenticate { user =>
      authorizeScope(ScopedAction(Domain.Projects, Action.Read, None), user) {
        authorizeAsync {
          ProjectDao
            .authProjectLayerExist(projectId, layerId, user, ActionType.View)
            .transact(xa)
            .unsafeToFuture
        } {
          rejectEmptyResponse {
            complete {
              SceneToLayerDao
                .getMosaicDefinition(layerId)
                .transact(xa)
                .unsafeToFuture
            }
          }
        }
      }
    }

  def getProjectLayerSceneColorCorrectParams(
      projectId: UUID,
      layerId: UUID,
      sceneId: UUID
  ): Route =
    authenticate { user =>
      authorizeScope(ScopedAction(Domain.Projects, Action.Read, None), user) {
        authorizeAsync {
          ProjectDao
            .authProjectLayerExist(projectId, layerId, user, ActionType.View)
            .transact(xa)
            .unsafeToFuture
        } {
          complete {
            SceneToLayerDao
              .getColorCorrectParams(layerId, sceneId)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }

  def setProjectLayerSceneColorCorrectParams(
      projectId: UUID,
      layerId: UUID,
      sceneId: UUID
  ): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Projects, Action.ColorCorrect, None),
        user
      ) {
        authorizeAsync {
          ProjectDao
            .authProjectLayerExist(projectId, layerId, user, ActionType.Edit)
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[ColorCorrect.Params]) { ccParams =>
            onSuccess(
              SceneToLayerDao
                .setColorCorrectParams(layerId, sceneId, ccParams)
                .transact(xa)
                .unsafeToFuture
            ) { _ =>
              complete(StatusCodes.NoContent)
            }
          }
        }
      }
    }

  def setProjectLayerScenesColorCorrectParams(
      projectId: UUID,
      layerId: UUID
  ): Route =
    authenticate { user =>
      authorizeScope(ScopedAction(Domain.Projects, Action.Update, None), user) {
        authorizeAsync {
          ProjectDao
            .authProjectLayerExist(projectId, layerId, user, ActionType.Edit)
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[BatchParams]) { params =>
            onSuccess(
              SceneToLayerDao
                .setColorCorrectParamsBatch(layerId, params)
                .transact(xa)
                .unsafeToFuture
            ) { _ =>
              complete(StatusCodes.NoContent)
            }
          }
        }
      }
    }

  def setProjectLayerSceneOrder(projectId: UUID, layerId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Projects, Action.ColorCorrect, None),
        user
      ) {
        authorizeAsync {
          ProjectDao
            .authProjectLayerExist(projectId, layerId, user, ActionType.Edit)
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[Seq[UUID]]) { sceneIds =>
            if (sceneIds.length > BULK_OPERATION_MAX_LIMIT) {
              complete(StatusCodes.RequestEntityTooLarge)
            }

            onSuccess(
              SceneToLayerDao
                .setManualOrder(layerId, sceneIds)
                .transact(xa)
                .unsafeToFuture
            ) { _ =>
              complete(StatusCodes.NoContent)
            }
          }
        }
      }
    }

  def listLayerScenes(projectId: UUID, layerId: UUID): Route = authenticate {
    user =>
      authorizeScope(ScopedAction(Domain.Projects, Action.Read, None), user) {
        authorizeAuthResultAsync {
          ProjectDao
            .authorized(user, ObjectType.Project, projectId, ActionType.View)
            .transact(xa)
            .unsafeToFuture
        } {
          (withPagination & projectSceneQueryParameters) {
            (page, sceneParams) =>
              complete {
                ProjectLayerScenesDao
                  .listLayerScenes(layerId, page, sceneParams)
                  .transact(xa)
                  .unsafeToFuture
              }
          }
        }
      }
  }

  def listLayerDatasources(projectId: UUID, layerId: UUID): Route =
    authenticate { user =>
      authorizeScope(ScopedAction(Domain.Projects, Action.Read, None), user) {
        (projectQueryParameters) { projectQueryParams =>
          authorizeAsync {
            val authorized = for {
              authProject <- ProjectDao.authorized(
                user,
                ObjectType.Project,
                projectId,
                ActionType.View
              )
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
              ProjectLayerDatasourcesDao
                .listProjectLayerDatasources(layerId)
                .transact(xa)
                .unsafeToFuture
            }
          }
        }
      }
    }

  def getProjectLayerSceneCounts(projectId: UUID): Route =
    authenticate { user =>
      authorizeScope(ScopedAction(Domain.Projects, Action.Read, None), user) {
        authorizeAuthResultAsync {
          ProjectDao
            .authorized(user, ObjectType.Project, projectId, ActionType.View)
            .transact(xa)
            .unsafeToFuture
        } {
          complete {
            ProjectLayerScenesDao
              .countLayerScenes(projectId)
              .transact(xa)
              .map(Map(_: _*))
              .unsafeToFuture
          }
        }
      }
    }

  def setProjectLayerColorMode(projectId: UUID, layerId: UUID) =
    authenticate { user =>
      authorizeScope(ScopedAction(Domain.Projects, Action.Update, None), user) {
        authorizeAuthResultAsync {
          ProjectDao
            .authorized(user, ObjectType.Project, projectId, ActionType.Edit)
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[ProjectColorModeParams]) { colorBands =>
            val setProjectLayerColorBandsIO = for {
              rowsAffected <- SceneToLayerDao
                .setProjectLayerColorBands(layerId, colorBands)
            } yield {
              rowsAffected
            }

            onSuccess(setProjectLayerColorBandsIO.transact(xa).unsafeToFuture) {
              _ =>
                complete(StatusCodes.NoContent)
            }
          }
        }
      }
    }
}
