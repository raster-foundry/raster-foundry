package com.azavea.rf.token

import akka.http.scaladsl.server.Route

import com.azavea.rf.auth.Authentication
import com.azavea.rf.utils.UserErrorHandler


/**
  * Routes for tokens
  */
trait TokenRoutes extends Authentication
  with UserErrorHandler
  with Auth0ErrorHandler {

  val tokenRoutes: Route = (handleExceptions(auth0ExceptionHandler) & handleExceptions(userExceptionHandler)) {
    pathEndOrSingleSlash {
      get { listRefreshTokens } ~
      post { getAuthorizedToken }
    } ~
    pathPrefix(Segment) { deviceId =>
      pathEndOrSingleSlash {
        delete { revokeRefreshToken(deviceId) }
      }
    }
  }

  def listRefreshTokens: Route = authenticate { user =>
    complete {
      TokenService.listRefreshTokens(user)
    }
  }

  def getAuthorizedToken: Route = {
    entity(as[RefreshToken]) { refreshToken =>
      complete {
        TokenService.getAuthorizedToken(refreshToken)
      }
    }
  }

  def revokeRefreshToken(deviceId: String): Route = authenticate { user =>
    complete {
      TokenService.revokeRefreshToken(user, deviceId)
    }
  }
}
