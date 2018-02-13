package com.azavea.rf.api.maptoken

import java.util.UUID

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import cats.effect.IO
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import com.azavea.rf.common.{Authentication, CommonHandlers, UserErrorHandler}
import com.azavea.rf.database.MapTokenDao
import com.azavea.rf.datamodel._
import io.circe._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import com.azavea.rf.database.filter.Filterables._
import doobie.util.transactor.Transactor


trait MapTokenRoutes extends Authentication
    with MapTokensQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler {

  implicit def xa: Transactor[IO]

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
        MapTokenDao.query.filter(mapTokenParams).filter(user).page(page)
      }
    }
  }

  def createMapToken: Route = authenticate { user =>
    entity(as[MapToken.Create]) { newMapToken =>
      authorize(user.isInRootOrSameOrganizationAs(newMapToken)) {
        onSuccess(MapTokenDao.insertMapToken(newMapToken, user)) { mapToken =>
          complete((StatusCodes.Created, mapToken))
        }
      }
    }
  }

  def getMapToken(mapTokenId: UUID): Route = authenticate { user =>
    get {
      rejectEmptyResponse {
        complete {
          MapTokenDao.query.filter(user).selectOption(mapTokenId)
        }
      }
    }
  }

  def updateMapToken(mapTokenId: UUID): Route = authenticate { user =>
    entity(as[MapToken]) { updatedMapToken =>
      authorize(user.isInRootOrSameOrganizationAs(updatedMapToken)) {
        onSuccess(MapTokenDao.updateMapToken(updatedMapToken, mapTokenId, user)) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteMapToken(mapTokenId: UUID): Route = authenticate { user =>
    onSuccess(MapTokenDao.deleteMapToken(mapTokenId, user)) {
      completeSingleOrNotFound
    }
  }
}
