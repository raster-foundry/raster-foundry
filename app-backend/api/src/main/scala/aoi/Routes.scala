package com.azavea.rf.api.aoi

import com.azavea.rf.api.utils.queryparams.QueryParametersCommon
import com.azavea.rf.common.{Authentication, CommonHandlers, UserErrorHandler}
import com.azavea.rf.database._
import com.azavea.rf.datamodel._

import akka.http.scaladsl.server.Route
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import cats.effect.IO
import com.azavea.rf.database.filter.Filterables._
import doobie._
import doobie.implicits._
import doobie.Fragments.in
import doobie.postgres._
import doobie.postgres.implicits._

import java.util.UUID


trait AoiRoutes extends Authentication
    with UserErrorHandler
    with QueryParametersCommon
    with PaginationDirectives
    with CommonHandlers {
  implicit def xa: Transactor[IO]

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
        AoiDao.query.filter(aoiQueryParams).filter(user).page(page).transact(xa).unsafeToFuture
      }
    }
  }

  def getAOI(id: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        AoiDao.query.filter(user).selectOption(id).transact(xa).unsafeToFuture
      }
    }
  }

  def updateAOI(id: UUID): Route = authenticate { user =>
    entity(as[AOI]) { aoi =>
      authorize(user.isInRootOrSameOrganizationAs(aoi)) {
        onSuccess(AoiDao.updateAOI(aoi, id, user).transact(xa).unsafeToFuture) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteAOI(id: UUID): Route = authenticate { user =>
    onSuccess(AoiDao.deleteAOI(id, user).transact(xa).unsafeToFuture) {
      completeSingleOrNotFound
    }
  }
}
