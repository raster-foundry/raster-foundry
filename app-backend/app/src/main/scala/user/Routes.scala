package com.azavea.rf.user

import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}
import java.net.URLDecoder

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes

import com.lonelyplanet.akka.http.extensions.PaginationDirectives

import com.azavea.rf.auth.Authentication
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.Users
import com.azavea.rf.utils.UserErrorHandler
import com.azavea.rf.datamodel._

/**
  * Routes for users
  */
trait UserRoutes extends Authentication with PaginationDirectives with UserErrorHandler {

  implicit def database: Database

  val userRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listUsers } ~
      post { createUser }
    } ~
    pathPrefix(Segment) { authIdEncoded =>
      pathEndOrSingleSlash {
        get { getUserByEncodedAuthId(authIdEncoded) }
      }
    }
  }

  def listUsers: Route = authenticate { user =>
    withPagination { page =>
      complete {
        Users.getPaginatedUsers(page)
      }
    }
  }

  // TODO: Restrict to users with correct permissions, e.g. admin
  def createUser: Route = authenticate { admin =>
    entity(as[User.Create]) { newUser =>
      onSuccess(Users.createUser(newUser)) { createdUser =>
        onSuccess(Users.getUserWithOrgsById(createdUser.id)) {
          case Some(user) => complete(StatusCodes.Created, user)
          case None => throw new IllegalStateException("Unable to create user")
        }
      }
    }
  }

  def getUserByEncodedAuthId(authIdEncoded: String): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        val authId = URLDecoder.decode(authIdEncoded, "US_ASCII")
        Users.getUserWithOrgsById(authId)
      }
    }
  }
}
