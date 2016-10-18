package com.azavea.rf.token

import akka.http.scaladsl.server.Route

import com.azavea.rf.auth.Authentication


/**
  * Routes for tokens
  */
trait TokenRoutes extends Authentication
  with Auth0ErrorHandler {

  def tokenRoutes: Route = pathPrefix("api" / "tokens") {
    handleExceptions(auth0ExceptionHandler) {
      listRefreshTokens ~
      getAuthorizedToken ~
      revokeRefreshToken
    }
  }

  def listRefreshTokens: Route = pathEndOrSingleSlash {
    get {
      authenticate { user =>
        onSuccess(TokenService.listRefreshTokens(user)) { tokens =>
          complete(tokens)
        }
      }
    }
  }

  def getAuthorizedToken: Route = pathEndOrSingleSlash {
    post {
      entity(as[RefreshToken]) { refreshToken =>
        onSuccess(TokenService.getAuthorizedToken(refreshToken)) { token =>
          complete(token)
        }
      }
    }
  }

  def revokeRefreshToken: Route = pathPrefix(Segment) { deviceId =>
    delete {
      authenticate { user =>
        onSuccess(TokenService.revokeRefreshToken(user, deviceId)) { response =>
          complete(response)
        }
      }
    }
  }
}
