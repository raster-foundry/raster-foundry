package com.azavea.rf.api.maptoken

import java.util.UUID

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import com.azavea.rf.common.{Authentication, UserErrorHandler, CommonHandlers}
import com.azavea.rf.database.tables.{Images, MapTokens}
import com.azavea.rf.database.{ActionRunner, Database}
import com.azavea.rf.datamodel._
import io.circe._
import de.heikoseeberger.akkahttpcirce.CirceSupport._


trait MapTokenRoutes extends Authentication
    with MapTokensQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler
    with ActionRunner {

  implicit def database: Database

  val mapTokenRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listMapTokens } ~
      post { createMapToken }
    } ~
    pathPrefix(JavaUUID) { mapTokenId =>
      get { getMapToken(mapTokenId) } ~
      put { updateMapToken(mapTokenId) } ~
      delete { deleteMapToken(mapTokenId) }
    }
  }


  def listMapTokens: Route = authenticate { user =>
    (withPagination & mapTokenQueryParams) { (page, mapTokenParams) =>
      complete {
        list[MapToken](
          MapTokens.listMapTokens(page.offset, page.limit, user, mapTokenParams),
          page.offset,
          page.limit
        )
      }
    }
  }

  def createMapToken: Route = authenticate { user =>
    entity(as[MapToken.Create]) { newMapToken =>
      authorize(user.isInRootOrSameOrganizationAs(newMapToken)) {
        onSuccess(write[MapToken](MapTokens.insertMapToken(newMapToken, user))) { mapToken =>
          complete((StatusCodes.Created, mapToken))
        }
      }
    }
  }

  def getMapToken(mapTokenId: UUID): Route = authenticate { user =>
    get {
      rejectEmptyResponse {
        complete {
          readOne[MapToken](MapTokens.getMapToken(mapTokenId, user))
        }
      }
    }
  }

  def updateMapToken(mapTokenId: UUID): Route = authenticate { user =>
    entity(as[MapToken]) { updatedMapToken =>
      authorize(user.isInRootOrSameOrganizationAs(updatedMapToken)) {
        onSuccess(update(MapTokens.updateMapToken(updatedMapToken, mapTokenId, user))) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteMapToken(mapTokenId: UUID): Route = authenticate { user =>
    onSuccess(drop(MapTokens.deleteMapToken(mapTokenId, user))) {
      completeSingleOrNotFound
    }
  }
}
