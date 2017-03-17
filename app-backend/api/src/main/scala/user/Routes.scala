package com.azavea.rf.api.user

import java.net.URLDecoder

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes

import com.lonelyplanet.akka.http.extensions.PaginationDirectives

import com.azavea.rf.common.{Authentication, UserErrorHandler}
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.Users
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
    pathPrefix("me") {
      get {
        getAuth0User
      } ~
      patch {
        updateAuth0User
      }
    } ~
    pathPrefix(Segment) { authIdEncoded =>
      pathEndOrSingleSlash {
        get { getUserByEncodedAuthId(authIdEncoded) } ~
        put { updateUserByEncodedAuthId(authIdEncoded) }
      }
    }
  }

  def listUsers: Route = authenticate { user =>
    withPagination { page =>
      complete {
        Users.listUsers(page)
      }
    }
  }

  // TODO: Restrict to users with correct permissions, e.g. admin
  def createUser: Route = authenticate { admin =>
    entity(as[User.Create]) { newUser =>
      onSuccess(Users.createUser(newUser)) { createdUser =>
        onSuccess(Users.getUserById(createdUser.id)) {
          case Some(user) => complete((StatusCodes.Created, user))
          case None => throw new IllegalStateException("Unable to create user")
        }
      }
    }
  }

  def getAuth0User: Route = authenticate { user =>
    complete {
      Auth0UserService.getAuth0User(user)
    }
  }
  def updateAuth0User: Route = authenticate {
    user =>
    entity(as[Auth0UserUpdate]) { userUpdate =>
      complete {
        Auth0UserService.updateAuth0User(user, userUpdate)
      }
    }
  }

  def getUserByEncodedAuthId(authIdEncoded: String): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        val authId = URLDecoder.decode(authIdEncoded, "US_ASCII")
        Users.getUserById(authId)
      }
    }
  }

  // TODO: Restrict to users with correct permissions, e.g. admin
  def updateUserByEncodedAuthId(authIdEncoded: String): Route = authenticate { admin =>
    entity(as[User]) { updatedUser =>
      onSuccess(Users.updateUser(updatedUser, authIdEncoded)) {
        case 1 => complete(StatusCodes.NoContent)
        case count => throw new IllegalStateException(
          s"Error updating user: update result expected to be: 1, was $count"
        )
      }
    }
  }
}
