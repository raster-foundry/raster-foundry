package com.azavea.rf.token

import akka.http.scaladsl.server.Route
import com.azavea.rf.auth.Authentication


/**
  * Routes for tokens
  */
trait TokenRoutes extends Authentication {

  import TokenJsonProtocol._

  def tokenRoutes: Route = pathPrefix("api" / "tokens") {
    listRefreshTokens
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
}
