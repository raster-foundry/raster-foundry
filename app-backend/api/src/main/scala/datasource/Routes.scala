package com.azavea.rf.api.datasource

import java.util.UUID

import scala.util.{Success, Failure}

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes

import com.lonelyplanet.akka.http.extensions.{PaginationDirectives, PageRequest}

import com.azavea.rf.common.{Authentication, UserErrorHandler, CommonHandlers}
import com.azavea.rf.database.tables.Datasources
import com.azavea.rf.database.query._
import com.azavea.rf.database.{Database, ActionRunner}
import com.azavea.rf.datamodel._
import io.circe._
import de.heikoseeberger.akkahttpcirce.CirceSupport._

trait DatasourceRoutes extends Authentication
    with DatasourceQueryParameterDirective
    with PaginationDirectives
    with UserErrorHandler
    with CommonHandlers
    with ActionRunner {
  implicit def database: Database

  val datasourceRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listDatasources } ~
      post { createDatasource }
    } ~
    pathPrefix(JavaUUID) { datasourceId =>
      get { getDatasource(datasourceId) } ~
      put { updateDatasource(datasourceId) } ~
      delete { deleteDatasource(datasourceId) }
    }
  }

  def listDatasources: Route = authenticate { user =>
    (withPagination & datasourceQueryParams) {
      (page: PageRequest, datasourceParams: DatasourceQueryParameters) =>
      complete {
        list[Datasource](Datasources.listDatasources(page.offset, page.limit, datasourceParams, user),
                         page.offset,
                         page.limit)
      }
    }
  }

  def getDatasource(datasourceId: UUID): Route = authenticate { user =>
    get {
      rejectEmptyResponse {
        complete {
          readOne[Datasource](Datasources.getDatasource(datasourceId, user))
        }
      }
    }
  }

  def createDatasource: Route = authenticate { user =>
    entity(as[Datasource.Create]) { newDatasource =>
      authorize(user.isInRootOrSameOrganizationAs(newDatasource)) {
        onSuccess(write[Datasource](Datasources.insertDatasource(newDatasource, user))) { datasource =>
          complete((StatusCodes.Created, datasource))
        }
      }
    }
  }

  def updateDatasource(datasourceId: UUID): Route = authenticate { user =>
    entity(as[Datasource]) { updateDatasource =>
      authorize(user.isInRootOrSameOrganizationAs(updateDatasource)) {
        onSuccess(update(Datasources.updateDatasource(updateDatasource, datasourceId, user))) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteDatasource(datasourceId: UUID): Route = authenticate { user =>
    onSuccess(drop(Datasources.deleteDatasource(datasourceId, user))) {
      completeSingleOrNotFound
    }
  }
}
