package com.azavea.rf.api.maptoken

import java.util.UUID

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import cats.effect.IO
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import com.azavea.rf.authentication.Authentication
import com.azavea.rf.common.{CommonHandlers, UserErrorHandler}
import com.azavea.rf.database._
import io.circe._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._

import doobie.util.transactor.Transactor
import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.datamodel._
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.Fragments
import doobie.postgres._
import doobie.postgres.implicits._

import scala.concurrent.Future

trait MapTokenRoutes
    extends Authentication
    with MapTokensQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler {

  val xa: Transactor[IO]

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
        MapTokenDao
          .listAuthorizedMapTokens(user, mapTokenParams, page)
          .transact(xa)
          .unsafeToFuture
      }
    }
  }

  def createMapToken: Route = authenticate { user =>
    entity(as[MapToken.Create]) { newMapToken =>
      authorizeAsync {
        val authIO = (newMapToken.project, newMapToken.toolRun) match {
          case (None, None) => false.pure[ConnectionIO]
          case (Some(projectId), None) =>
            ProjectDao.authorized(user,
                                  ObjectType.Project,
                                  projectId,
                                  ActionType.Edit)
          case (None, Some(toolRunId)) =>
            ToolRunDao.authorized(user,
                                  ObjectType.Analysis,
                                  toolRunId,
                                  ActionType.Edit)
          case _ => false.pure[ConnectionIO]
        }
        authIO.transact(xa).unsafeToFuture
      } {
        onSuccess(
          MapTokenDao.insert(newMapToken, user).transact(xa).unsafeToFuture) {
          mapToken =>
            complete((StatusCodes.Created, mapToken))
        }
      }
    }
  }

  def getMapToken(mapTokenId: UUID): Route = authenticate { user =>
    authorizeAsync {
      MapTokenDao
        .authorize(mapTokenId, user, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      get {
        rejectEmptyResponse {
          complete {
            MapTokenDao.query
              .filter(mapTokenId)
              .selectOption
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }
  }

  def updateMapToken(mapTokenId: UUID): Route = authenticate { user =>
    authorizeAsync {
      MapTokenDao
        .authorize(mapTokenId, user, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[MapToken]) { updatedMapToken =>
        onSuccess(
          MapTokenDao
            .update(updatedMapToken, mapTokenId, user)
            .transact(xa)
            .unsafeToFuture) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteMapToken(mapTokenId: UUID): Route = authenticate { user =>
    authorizeAsync {
      MapTokenDao
        .authorize(mapTokenId, user, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
    } {
      onSuccess(
        MapTokenDao.query
          .filter(mapTokenId)
          .delete
          .transact(xa)
          .unsafeToFuture) {
        completeSingleOrNotFound
      }
    }
  }
}
