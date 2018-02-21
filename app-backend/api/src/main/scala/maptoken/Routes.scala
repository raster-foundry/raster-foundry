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

import doobie.util.transactor.Transactor
import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.datamodel._
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.Fragments.in
import doobie.postgres._
import doobie.postgres.implicits._



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
        MapTokenDao.query.filter(mapTokenParams).filter(user).page(page).transact(xa).unsafeToFuture
      }
    }
  }

  def createMapToken: Route = authenticate { user =>
    entity(as[MapToken.Create]) { newMapToken =>
      authorize(user.isInRootOrSameOrganizationAs(newMapToken)) {
        onSuccess(MapTokenDao.insertMapToken(newMapToken, user).transact(xa).unsafeToFuture) { mapToken =>
          complete((StatusCodes.Created, mapToken))
        }
      }
    }
  }

  def getMapToken(mapTokenId: UUID): Route = authenticate { user =>
    get {
      rejectEmptyResponse {
        complete {
          MapTokenDao.query.filter(user).selectOption(mapTokenId).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def updateMapToken(mapTokenId: UUID): Route = authenticate { user =>
    entity(as[MapToken]) { updatedMapToken =>
      authorize(user.isInRootOrSameOrganizationAs(updatedMapToken)) {
        onSuccess(MapTokenDao.updateMapToken(updatedMapToken, mapTokenId, user).transact(xa).unsafeToFuture) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteMapToken(mapTokenId: UUID): Route = authenticate { user =>
    onSuccess(MapTokenDao.deleteMapToken(mapTokenId, user).transact(xa).unsafeToFuture) {
      completeSingleOrNotFound
    }
  }
}
