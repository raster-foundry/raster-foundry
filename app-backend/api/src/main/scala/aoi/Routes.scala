package com.rasterfoundry.api.aoi

import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.akkautil.{
  Authentication,
  CommonHandlers,
  UserErrorHandler
}
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.server.Route
import com.rasterfoundry.akkautil.PaginationDirectives
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import cats.effect.IO
import com.rasterfoundry.database.filter.Filterables._
import doobie._
import doobie.implicits._

import java.util.UUID

trait AoiRoutes
    extends Authentication
    with UserErrorHandler
    with QueryParametersCommon
    with PaginationDirectives
    with CommonHandlers {

  val xa: Transactor[IO]

  val aoiRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listAOIs }
    } ~
      pathPrefix(JavaUUID) { aoiId =>
        pathEndOrSingleSlash {
          get { getAOI(aoiId) } ~
            put { updateAOI(aoiId) } ~
            delete { deleteAOI(aoiId) }
        }
      }
  }

  def listAOIs: Route = authenticate { user =>
    (withPagination & aoiQueryParameters) { (page, aoiQueryParams) =>
      complete {
        AoiDao
          .listAuthorizedAois(user, aoiQueryParams, page)
          .transact(xa)
          .unsafeToFuture
      }
    }
  }

  def getAOI(id: UUID): Route = authenticate { user =>
    authorizeAsync {
      AoiDao.authorize(id, user, ActionType.View).transact(xa).unsafeToFuture
    } {
      rejectEmptyResponse {
        complete {
          AoiDao.query.filter(id).selectOption.transact(xa).unsafeToFuture
        }
      }
    }
  }

  def updateAOI(id: UUID): Route = authenticate { user =>
    authorizeAsync {
      AoiDao.authorize(id, user, ActionType.Edit).transact(xa).unsafeToFuture
    } {
      entity(as[AOI]) { aoi =>
        onSuccess(AoiDao.updateAOI(aoi, user).transact(xa).unsafeToFuture) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteAOI(id: UUID): Route = authenticate { user =>
    authorizeAsync {
      AoiDao.authorize(id, user, ActionType.Edit).transact(xa).unsafeToFuture
    } {
      onSuccess(AoiDao.deleteAOI(id).transact(xa).unsafeToFuture) {
        completeSingleOrNotFound
      }
    }
  }
}
