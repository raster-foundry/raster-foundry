package com.azavea.rf.user

import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}

import akka.http.scaladsl.server.{Route, ExceptionHandler}
import akka.http.scaladsl.model.StatusCodes

import com.lonelyplanet.akka.http.extensions.PaginationDirectives

import com.azavea.rf.auth.Authentication
import com.azavea.rf.utils.Database

/**
  * Routes for users
  */
trait UserRoutes extends Authentication with PaginationDirectives {

  implicit def database: Database
  implicit val ec: ExecutionContext

  val userExceptionHandler = ExceptionHandler {
    case e: UserErrorException =>
      complete(StatusCodes.ClientError(400)("", e.getMessage()))
  }

  def userRoutes:Route = {
    handleExceptions(userExceptionHandler) {
      authenticate { user =>
        pathPrefix("api" / "users") {
          pathEndOrSingleSlash {
            withPagination { page =>
              get {
                onSuccess(UserService.getPaginatedUsers(page)) { resp =>
                  complete(resp)
                }
              }
            } ~
              post {
                //TODO: This should only be accessible by users with the correct permission
                //      (IE admin in the "Public" org)
                entity(as[UsersRowCreate]) { newUser =>
                  onSuccess(UserService.createUser(newUser)) {
                    case Success(user) => onSuccess(UserService.getUserWithOrgsById(user.id)) {
                      case Some(user) => complete((StatusCodes.Created, user))
                      case None => complete(StatusCodes.InternalServerError)
                    }
                    case Failure(_) => complete(StatusCodes.InternalServerError)
                  }
                }
              }
          } ~
            pathPrefix(Segment) { authIdEncoded =>
              val authId = java.net.URLDecoder.decode(authIdEncoded, "US_ASCII")
              pathEndOrSingleSlash {
                get {
                  onSuccess(
                    UserService.getUserWithOrgsById(authId)
                  ) { resp =>
                    resp match {
                      case Some(user) => complete(user)
                      case _ => complete((StatusCodes.NotFound))
                    }
                  }
                }
              }
            }
        }
      }
    }
  }
}
