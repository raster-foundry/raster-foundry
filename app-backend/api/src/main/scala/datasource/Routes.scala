package com.rasterfoundry.api.datasource

import com.rasterfoundry.akkautil._
import com.rasterfoundry.database._
import com.rasterfoundry.database.filter.Filterables._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import cats.effect.IO
import cats.implicits._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._

import scala.concurrent.ExecutionContext

import java.util.UUID

trait DatasourceRoutes
    extends Authentication
    with DatasourceQueryParameterDirective
    with PaginationDirectives
    with UserErrorHandler
    with CommonHandlers {
  val xa: Transactor[IO]

  implicit val ec: ExecutionContext

  val datasourceRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listDatasources } ~
        post { createDatasource }
    } ~
      pathPrefix(JavaUUID) { datasourceId =>
        pathEndOrSingleSlash {
          get { getDatasource(datasourceId) } ~
            put { updateDatasource(datasourceId) } ~
            delete { deleteDatasource(datasourceId) }
        } ~
          pathPrefix("permissions") {
            pathEndOrSingleSlash {
              put {
                replaceDatasourcePermissions(datasourceId)
              }
            } ~
              post {
                addDatasourcePermission(datasourceId)
              } ~
              get {
                listDatasourcePermissions(datasourceId)
              } ~
              delete {
                deleteDatasourcePermissions(datasourceId)
              }
          } ~
          pathPrefix("actions") {
            pathEndOrSingleSlash {
              get {
                listUserDatasourceActions(datasourceId)
              }
            }
          }
      }
  }

  def listDatasources: Route =
    authenticate {
      case (user, _) =>
        authorizeScope(
          ScopedAction(Domain.Datasources, Action.Read, None),
          user
        ) {
          (withPagination & datasourceQueryParams) {
            (page: PageRequest, datasourceParams: DatasourceQueryParameters) =>
              complete {
                DatasourceDao
                  .authQuery(
                    user,
                    ObjectType.Datasource,
                    datasourceParams.ownershipTypeParams.ownershipType,
                    datasourceParams.groupQueryParameters.groupType,
                    datasourceParams.groupQueryParameters.groupId
                  )
                  .filter(datasourceParams)
                  .page(page)
                  .transact(xa)
                  .unsafeToFuture
              }
          }
        }
    }

  def getDatasource(datasourceId: UUID): Route =
    authenticate {
      case (user, _) =>
        authorizeScope(
          ScopedAction(Domain.Datasources, Action.Read, None),
          user
        ) {
          authorizeAuthResultAsync {
            DatasourceDao
              .authorized(
                user,
                ObjectType.Datasource,
                datasourceId,
                ActionType.View
              )
              .transact(xa)
              .unsafeToFuture
          } {
            rejectEmptyResponse {
              complete {
                DatasourceDao
                  .getDatasourceById(datasourceId)
                  .transact(xa)
                  .unsafeToFuture
              }
            }
          }
        }
    }

  def createDatasource: Route =
    authenticate {
      case (user, _) =>
        authorizeScope(
          ScopedAction(Domain.Datasources, Action.Create, None),
          user
        ) {
          entity(as[Datasource.Create]) { newDatasource =>
            onSuccess(
              DatasourceDao
                .createDatasource(newDatasource, user)
                .transact(xa)
                .unsafeToFuture
            ) { datasource =>
              complete((StatusCodes.Created, datasource))
            }
          }
        }
    }

  def updateDatasource(datasourceId: UUID): Route =
    authenticate {
      case (user, _) =>
        authorizeScope(
          ScopedAction(Domain.Datasources, Action.Update, None),
          user
        ) {
          authorizeAuthResultAsync(
            DatasourceDao
              .authorized(
                user,
                ObjectType.Datasource,
                datasourceId,
                ActionType.Edit
              )
              .transact(xa)
              .unsafeToFuture
          ) {
            entity(as[Datasource]) { updateDatasource =>
              onSuccess(
                DatasourceDao
                  .updateDatasource(updateDatasource, datasourceId)
                  .transact(xa)
                  .unsafeToFuture
              ) {
                completeSingleOrNotFound
              }
            }
          }
        }
    }

  def deleteDatasource(datasourceId: UUID): Route =
    authenticate {
      case (user, _) =>
        authorizeScope(
          ScopedAction(Domain.Datasources, Action.Delete, None),
          user
        ) {
          authorizeAsync {
            DatasourceDao
              .isDeletable(datasourceId, user)
              .transact(xa)
              .unsafeToFuture
          } {
            onSuccess(
              DatasourceDao
                .deleteDatasourceWithRelated(datasourceId)
                .transact(xa)
                .unsafeToFuture
            ) { counts: List[Int] =>
              complete(
                StatusCodes.OK -> s"${counts(1)} uploads deleted, ${counts(2)} scenes deleted. ${counts(0)} datasources deleted."
              )
            }
          }
        }
    }

  def listDatasourcePermissions(datasourceId: UUID): Route =
    authenticate {
      case (user, _) =>
        authorizeScope(
          ScopedAction(Domain.Datasources, Action.ReadPermissions, None),
          user
        ) {
          authorizeAuthResultAsync {
            DatasourceDao
              .authorized(
                user,
                ObjectType.Datasource,
                datasourceId,
                ActionType.View
              )
              .transact(xa)
              .unsafeToFuture
          } {
            complete {
              DatasourceDao
                .getPermissions(datasourceId)
                .transact(xa)
                .unsafeToFuture
            }
          }
        }
    }

  def replaceDatasourcePermissions(datasourceId: UUID): Route =
    authenticate {
      case (user, _) =>
        authorizeScope(
          ScopedAction(Domain.Datasources, Action.Share, None),
          user
        ) {
          entity(as[List[ObjectAccessControlRule]]) { acrList =>
            authorizeAsync {
              (
                DatasourceDao.authorized(
                  user,
                  ObjectType.Datasource,
                  datasourceId,
                  ActionType.Edit
                ) map {
                  _.toBoolean
                },
                acrList traverse { acr =>
                  DatasourceDao.isValidPermission(acr, user)
                } map {
                  _.foldLeft(true)(_ && _)
                } map {
                  case true =>
                    DatasourceDao.isReplaceWithinScopedLimit(
                      Domain.Datasources,
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
                DatasourceDao
                  .replacePermissions(datasourceId, acrList)
                  .transact(xa)
                  .unsafeToFuture
              }
            }
          }
        }
    }

  def addDatasourcePermission(datasourceId: UUID): Route =
    authenticate {
      case (user, _) =>
        authorizeScope(
          ScopedAction(Domain.Datasources, Action.Share, None),
          user
        ) {
          entity(as[ObjectAccessControlRule]) { acr =>
            authorizeAsync {
              (
                DatasourceDao.authorized(
                  user,
                  ObjectType.Datasource,
                  datasourceId,
                  ActionType.Edit
                ) map {
                  _.toBoolean
                },
                DatasourceDao.isValidPermission(acr, user)
              ).tupled
                .map({ authTup =>
                  authTup._1 && authTup._2
                })
                .transact(xa)
                .unsafeToFuture
            } {
              complete {
                DatasourceDao
                  .addPermission(datasourceId, acr)
                  .transact(xa)
                  .unsafeToFuture
              }
            }
          }
        }
    }

  def listUserDatasourceActions(datasourceId: UUID): Route =
    authenticate {
      case (user, _) =>
        authorizeScope(
          ScopedAction(Domain.Datasources, Action.ReadPermissions, None),
          user
        ) {
          authorizeAuthResultAsync {
            DatasourceDao
              .authorized(
                user,
                ObjectType.Datasource,
                datasourceId,
                ActionType.View
              )
              .transact(xa)
              .unsafeToFuture
          } {
            onSuccess(
              DatasourceDao
                .unsafeGetDatasourceById(datasourceId)
                .transact(xa)
                .unsafeToFuture
            ) { datasource =>
              datasource.owner == user.id match {
                case true => complete(List("*"))
                case false =>
                  complete {
                    DatasourceDao
                      .listUserActions(user, datasourceId)
                      .transact(xa)
                      .unsafeToFuture
                  }
              }
            }
          }
        }
    }

  def deleteDatasourcePermissions(datasourceId: UUID): Route =
    authenticate {
      case (user, _) =>
        authorizeScope(
          ScopedAction(Domain.Datasources, Action.Share, None),
          user
        ) {
          authorizeAuthResultAsync {
            DatasourceDao
              .authorized(
                user,
                ObjectType.Datasource,
                datasourceId,
                ActionType.Edit
              )
              .transact(xa)
              .unsafeToFuture
          } {
            complete {
              DatasourceDao
                .deletePermissions(datasourceId)
                .transact(xa)
                .unsafeToFuture
            }
          }
        }
    }
}
