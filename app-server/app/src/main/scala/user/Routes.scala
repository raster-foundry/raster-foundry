package com.azavea.rf.user

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatchers.JavaUUID
import akka.http.scaladsl.model.StatusCodes

import com.lonelyplanet.akka.http.extensions.PaginationDirectives

import com.azavea.rf.auth.Authentication
import com.azavea.rf.datamodel.latest.schema.tables.UsersRow
import com.azavea.rf.utils.Database

/**
  * Routes for users
  */
trait UserRoutes extends Authentication with PaginationDirectives {

  implicit val database: Database
  implicit val ec: ExecutionContext

  def userRoutes:Route = {
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
            entity(as[UsersRowCreate]) { newUser =>
              onSuccess(UserService.createUser(newUser)) { resp =>
                complete((StatusCodes.Created, resp))
              }
            }
          }
        } ~
        pathPrefix(JavaUUID) { id =>
          pathEndOrSingleSlash {
            get {
              onSuccess(UserService.getUserById(id)) { resp =>
                resp match {
                  case Some(user) => complete(user)
                  case _ => complete((StatusCodes.NotFound))
                }
              }
            } ~
            put {
              entity(as[UsersRow]) { user =>
                onSuccess(UserService.updateUser(user, id)) { resp =>
                  resp match {
                    case 1 => complete((StatusCodes.NoContent))
                    case 0 => complete((StatusCodes.NotFound))
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
