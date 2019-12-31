package com.rasterfoundry.api.maptoken

import java.util.UUID

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import cats.effect.IO
import com.rasterfoundry.akkautil.PaginationDirectives
import com.rasterfoundry.akkautil.{
  Authentication,
  CommonHandlers,
  UserErrorHandler
}
import com.rasterfoundry.database._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._

import doobie.util.transactor.Transactor
import com.rasterfoundry.database.filter.Filterables._
import com.rasterfoundry.datamodel._
import cats.Applicative
import doobie._
import doobie.implicits._

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
    authorizeScope(ScopedAction(Domain.MapTokens, Action.Read, None), user) {
      (withPagination & mapTokenQueryParams) { (page, mapTokenParams) =>
        complete {
          MapTokenDao
            .listAuthorizedMapTokens(user, mapTokenParams, page)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def createMapToken: Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.MapTokens, Action.Create, None), user) {
      entity(as[MapToken.Create]) { newMapToken =>
        authorizeAsync {
          val authIO = (newMapToken.project, newMapToken.toolRun) match {
            case (None, None) =>
              Applicative[ConnectionIO].pure(false)
            case (Some(projectId), None) =>
              ProjectDao
                .authorized(
                  user,
                  ObjectType.Project,
                  projectId,
                  ActionType.Edit
                )
                .map(_.toBoolean)
            case (None, Some(toolRunId)) =>
              ToolRunDao
                .authorized(
                  user,
                  ObjectType.Analysis,
                  toolRunId,
                  ActionType.Edit
                )
                .map(_.toBoolean)
            case _ => Applicative[ConnectionIO].pure(false)
          }
          authIO.transact(xa).unsafeToFuture
        } {
          onSuccess(
            MapTokenDao.insert(newMapToken, user).transact(xa).unsafeToFuture
          ) { mapToken =>
            complete((StatusCodes.Created, mapToken))
          }
        }
      }
    }
  }

  def getMapToken(mapTokenId: UUID): Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.MapTokens, Action.Read, None), user) {
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
  }

  def updateMapToken(mapTokenId: UUID): Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.MapTokens, Action.Update, None), user) {
      authorizeAsync {
        MapTokenDao
          .authorize(mapTokenId, user, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[MapToken]) { updatedMapToken =>
          onSuccess(
            MapTokenDao
              .update(updatedMapToken, mapTokenId)
              .transact(xa)
              .unsafeToFuture
          ) {
            completeSingleOrNotFound
          }
        }
      }
    }
  }

  def deleteMapToken(mapTokenId: UUID): Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.MapTokens, Action.Delete, None), user) {
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
            .unsafeToFuture
        ) {
          completeSingleOrNotFound
        }
      }
    }
  }
}
