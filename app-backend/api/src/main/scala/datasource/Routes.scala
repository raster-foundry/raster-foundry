package com.azavea.rf.api.datasource

import java.util.UUID

import scala.util.{Success, Failure}

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes

import com.lonelyplanet.akka.http.extensions.{PaginationDirectives, PageRequest}

import com.azavea.rf.common.{Authentication, UserErrorHandler}
import com.azavea.rf.database.tables.Datasources
import com.azavea.rf.database.query._
import com.azavea.rf.database.{Database, ActionRunner}
import com.azavea.rf.datamodel._

trait DatasourceRoutes extends Authentication
    with DatasourceQueryParameterDirective
    with PaginationDirectives
    with UserErrorHandler
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
        list[Datasource](Datasources.listDatasources(page.offset, page.limit, datasourceParams),
                         page.offset,
                         page.limit)
      }
    }
  }

  def getDatasource(datasourceId: UUID): Route = authenticate { user =>
    get {
      rejectEmptyResponse {
        complete {
          readOne[Datasource](Datasources.getDatasource(datasourceId))
        }
      }
    }
  }

  def createDatasource: Route = authenticate { user =>
    entity(as[Datasource.Create]) { newDatasource =>
      onSuccess(write[Datasource](Datasources.insertDatasource(newDatasource, user))) { datasource =>
        complete(datasource)
      }
    }
  }

  def updateDatasource(datasourceId: UUID): Route = authenticate { user =>
    entity(as[Datasource]) { updateDatasource =>
      onSuccess(update(Datasources.updateDatasource(updateDatasource, datasourceId, user))) { count =>
        complete(StatusCodes.NoContent)
      }
    }
  }

  def deleteDatasource(datasourceId: UUID): Route = authenticate { user =>
    onSuccess(drop(Datasources.deleteDatasource(datasourceId))) {
      case 1 => complete(StatusCodes.NoContent)
      case 0 => complete(StatusCodes.NotFound)
      case count => throw new IllegalStateException(
        s"Error deleting datasource. Delete result expected to be 1, was $count"
      )
    }
  }
}
