package com.azavea.rf.api.aoi

import java.util.UUID

import akka.http.scaladsl.server.Route
import com.azavea.rf.api.utils.queryparams.QueryParametersCommon
import com.azavea.rf.common.{Authentication, CommonHandlers, UserErrorHandler}
import com.azavea.rf.database.tables.AOIs
import com.azavea.rf.datamodel._
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import de.heikoseeberger.akkahttpcirce.CirceSupport._

// --- //

trait AoiRoutes extends Authentication
    with UserErrorHandler
    with QueryParametersCommon
    with PaginationDirectives
    with CommonHandlers {

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
        AOIs.listAOIs(page, aoiQueryParams, user)
      }
    }
  }

  def getAOI(id: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        AOIs.getAOI(id, user).value
      }
    }
  }

  def updateAOI(id: UUID): Route = authenticate { user =>
    entity(as[AOI]) { aoi =>
      authorize(user.isInRootOrSameOrganizationAs(aoi)) {
        onSuccess(AOIs.updateAOI(aoi, id, user)) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteAOI(id: UUID): Route = authenticate { user =>
    onSuccess(AOIs.deleteAOI(id, user)) {
      completeSingleOrNotFound
    }
  }
}
