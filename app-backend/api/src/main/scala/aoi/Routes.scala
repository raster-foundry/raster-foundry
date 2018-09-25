package com.azavea.rf.api.aoi

import com.azavea.rf.api.utils.queryparams.QueryParametersCommon
import com.azavea.rf.common.{CommonHandlers, UserErrorHandler}
import com.azavea.rf.authentication.Authentication
import com.azavea.rf.database._
import com.azavea.rf.datamodel._

import akka.http.scaladsl.server.Route
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import cats.effect.IO
import cats.implicits._
import com.azavea.rf.database.filter.Filterables._
import doobie._
import doobie.implicits._
import doobie.Fragments.in
import doobie.postgres._
import doobie.postgres.implicits._

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
