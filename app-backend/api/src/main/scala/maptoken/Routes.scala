package com.azavea.rf.api.maptoken

import java.util.UUID

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import com.azavea.rf.common.{Authentication, UserErrorHandler}
import com.azavea.rf.database.tables.{Images, MapTokens}
import com.azavea.rf.database.{ActionRunner, Database}
import com.azavea.rf.datamodel._
import io.circe._
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.CirceSupport._


trait MapTokenRoutes extends Authentication
    with MapTokensQueryParameterDirective
    with PaginationDirectives
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
      onSuccess(write[MapToken](MapTokens.insertMapToken(newMapToken, user))) { mapToken =>
        complete(mapToken)
      }
    }
  }

  def getMapToken(mapTokenId: UUID): Route = authenticate { user =>
    get {
      rejectEmptyResponse {
        complete {
          readOne[MapToken](MapTokens.getMapToken(mapTokenId))
        }
      }
    }
  }

  def updateMapToken(mapTokenId: UUID): Route = authenticate { user =>
    entity(as[MapToken]) { updatedMapToken =>
      onSuccess(update(MapTokens.updateMapToken(updatedMapToken, mapTokenId, user))) { count =>
        complete(StatusCodes.NoContent)
      }
    }
  }

  def deleteMapToken(mapTokenId: UUID): Route = authenticate { user =>
    onSuccess(drop(MapTokens.deleteMapToken(mapTokenId))) {
      case 1 => complete(StatusCodes.NoContent)
      case 0 => complete(StatusCodes.NotFound)
      case count => throw new IllegalStateException(
        s"Error deleting image: delete result expected to be 1, was $count"
      )
    }
  }
}
