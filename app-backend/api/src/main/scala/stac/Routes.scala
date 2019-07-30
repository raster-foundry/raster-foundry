package com.rasterfoundry.api.stac

import com.rasterfoundry.akkautil._
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._
import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import cats.effect.IO

import java.util.UUID
import doobie._
import doobie.implicits._

trait StacRoutes
    extends Authentication
    with PaginationDirectives
    with UserErrorHandler
    with CommonHandlers
    with QueryParametersCommon {
  val xa: Transactor[IO]

  val stacRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listStacExports } ~
        post { createStacExport }
    } ~
      pathPrefix(JavaUUID) { stacExportId =>
        pathEndOrSingleSlash {
          get { getStacExport(stacExportId) } ~
            put { updateStacExport(stacExportId) } ~
            delete { deleteStacExport(stacExportId) }
        }
      }
  }

  def listStacExports: Route = authenticate { user =>
    (withPagination & stacExportQueryParameters) {
      (page: PageRequest, params: StacExportQueryParameters) =>
        complete {
          StacExportDao
            .list(page, params, user)
            .transact(xa)
            .unsafeToFuture
        }
    }
  }

  def createStacExport: Route = authenticate { user =>
    entity(as[StacExport.Create]) { newStacExport =>
      authorizeAsync {
        StacExportDao
          .hasProjectViewAccess(newStacExport.layerDefinitions, user)
          .transact(xa)
          .unsafeToFuture
      } {
        onSuccess(
          StacExportDao
            .create(newStacExport, user)
            .transact(xa)
            .unsafeToFuture) { stacExport =>
          // To be implemented: kickoffStacExport(stacExport.id)
          complete((StatusCodes.Created, stacExport))
        }
      }
    }
  }

  def getStacExport(id: UUID): Route = authenticate { user =>
    authorizeAsync {
      StacExportDao
        .isOwnerOrSuperUser(user, id)
        .transact(xa)
        .unsafeToFuture
    } {
      rejectEmptyResponse {
        complete {
          StacExportDao
            .getById(id)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def updateStacExport(id: UUID): Route = authenticate { user =>
    authorizeAsync {
      StacExportDao
        .isOwnerOrSuperUser(user, id)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[StacExport]) { updateStacExport =>
        onSuccess(
          StacExportDao
            .update(updateStacExport, id)
            .transact(xa)
            .unsafeToFuture) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteStacExport(id: UUID): Route = authenticate { user =>
    authorizeAsync {
      StacExportDao
        .isOwnerOrSuperUser(user, id)
        .transact(xa)
        .unsafeToFuture
    } {
      onSuccess(
        StacExportDao
          .delete(id)
          .transact(xa)
          .unsafeToFuture) { count: Int =>
        complete((StatusCodes.NoContent, s"$count stac export deleted"))
      }
    }
  }
}
