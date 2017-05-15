package com.azavea.rf.api.exports

import java.util.UUID

import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import cats.implicits._
import cats.data._
import com.lonelyplanet.akka.http.extensions.{PageRequest, PaginationDirectives}
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe._

import com.azavea.rf.common._
import com.azavea.rf.database.tables.Exports
import com.azavea.rf.database.query._
import com.azavea.rf.database.{ActionRunner, Database}
import com.azavea.rf.datamodel._

import scala.concurrent.ExecutionContext.Implicits.global

trait ExportRoutes extends Authentication
  with ExportQueryParameterDirective
  with PaginationDirectives
  with CommonHandlers
  with UserErrorHandler
  with LazyLogging
  with Airflow
  with ActionRunner {
  implicit def database: Database

  val exportRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listExports } ~
      post { createExport }
    } ~
    pathPrefix(JavaUUID) { exportId =>
      pathEndOrSingleSlash {
        get { getExport(exportId) } ~
        put { updateExport(exportId) } ~
        delete { deleteExport(exportId) }
      } ~
      pathPrefix("definition") {
        pathEndOrSingleSlash {
          get { getExportDefinition(exportId) }
        }
      }
    }
  }

  def listExports: Route = authenticate { user =>
    (withPagination & exportQueryParams) {
      (page: PageRequest, queryParams: ExportQueryParameters) =>
        complete {
          list[Export](Exports.listExports(page.offset, page.limit, queryParams, user),
            page.offset, page.limit)
        }
    }
  }

  def getExport(exportId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        readOne[Export](Exports.getExport(exportId, user))
      }
    }
  }

  def getExportDefinition(exportId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        readOne[Export](Exports.getExport(exportId, user))
          .map { _.map { Exports.getExportDefinition(_, user) } }
          .map(_.sequence.map(_.flatten)).flatten
      }
    }
  }

  def createExport: Route = authenticate { user =>
    entity(as[Export.Create]) { newExport =>
      authorize(user.isInRootOrSameOrganizationAs(newExport)) {
        onSuccess(write[Export](Exports.insertExport(newExport, user))) { export =>
          kickoffProjectExport(export.id)
          complete(export)
        }
      }
    }
  }

  def updateExport(uploadId: UUID): Route = authenticate { user =>
    entity(as[Export]) { updateExport =>
      authorize(user.isInRootOrSameOrganizationAs(updateExport)) {
        onSuccess(update(Exports.updateExport(updateExport, uploadId, user))) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteExport(exportId: UUID): Route = authenticate { user =>
    onSuccess(drop(Exports.deleteExport(exportId, user))) {
      completeSingleOrNotFound
    }
  }
}
