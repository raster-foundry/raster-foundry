package com.azavea.rf.api.datasource

import com.azavea.rf.api.utils.PermissionRouter
import com.azavea.rf.common.{Authentication, UserErrorHandler, CommonHandlers}
import com.azavea.rf.database._
import com.azavea.rf.datamodel._
import com.azavea.rf.database.filter.Filterables._

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import com.lonelyplanet.akka.http.extensions.{PaginationDirectives, PageRequest}
import io.circe._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import cats.effect.IO
import kamon.akka.http.KamonTraceDirectives

import java.util.UUID
import scala.util.{Success, Failure}
import doobie._
import doobie.implicits._
import doobie.Fragments.in
import doobie.postgres._
import doobie.postgres.implicits._



trait DatasourceRoutes extends Authentication
    with DatasourceQueryParameterDirective
    with PaginationDirectives
    with UserErrorHandler
    with CommonHandlers {
  val xa: Transactor[IO]

  private val datasourcePermissionRouter = PermissionRouter[Datasource](xa, DatasourceDao, ObjectType.Datasource)

  val datasourceRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listDatasources } ~
      post { createDatasource }
    } ~
    pathPrefix(JavaUUID) { datasourceId =>
      get { getDatasource(datasourceId) } ~
      put { updateDatasource(datasourceId) } ~
      delete { deleteDatasource(datasourceId) } ~
        pathPrefix("permissions") {
          pathEndOrSingleSlash {
            put {
              datasourcePermissionRouter.replacePermissions(datasourceId)
            }
          } ~
            post {
              datasourcePermissionRouter.addPermission(datasourceId)
            } ~
            get {
              datasourcePermissionRouter.listPermissions(datasourceId)
            }
        }
    }
  }

  def listDatasources: Route = authenticate { user =>
    (withPagination & datasourceQueryParams) { (page: PageRequest, datasourceParams: DatasourceQueryParameters) =>
      complete {
        DatasourceDao.query.filter(datasourceParams)
          .authorize(user, ObjectType.Datasource, ActionType.View)
          .page(page)
          .transact(xa).unsafeToFuture
      }
    }
  }

  def getDatasource(datasourceId: UUID): Route = authenticate { user =>
    authorizeAsync {
      DatasourceDao.query
        .authorized(user, ObjectType.Datasource, datasourceId, ActionType.View)
        .transact(xa).unsafeToFuture
    } {
      rejectEmptyResponse {
        complete {
          DatasourceDao.getDatasourceById(datasourceId, user).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def createDatasource: Route = authenticate { user =>
    entity(as[Datasource.Create]) { newDatasource =>
      onSuccess(DatasourceDao.createDatasource(newDatasource, user).transact(xa).unsafeToFuture) { datasource =>
        complete((StatusCodes.Created, datasource))
      }
    }
  }

  def updateDatasource(datasourceId: UUID): Route = authenticate { user =>
    authorizeAsync (
      DatasourceDao.query
        .authorized(user, ObjectType.Datasource, datasourceId, ActionType.Edit)
        .transact(xa).unsafeToFuture
    ) {
      entity(as[Datasource]) { updateDatasource =>
        onSuccess(DatasourceDao.updateDatasource(updateDatasource, datasourceId, user).transact(xa).unsafeToFuture) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteDatasource(datasourceId: UUID): Route = authenticate { user =>
    authorizeAsync {
      DatasourceDao.query
        .authorized(user, ObjectType.Datasource, datasourceId, ActionType.Delete)
        .transact(xa).unsafeToFuture
    } {
      onSuccess(DatasourceDao.query.filter(datasourceId).delete.transact(xa).unsafeToFuture) {
         completeSingleOrNotFound
      }
    }
  }
}
