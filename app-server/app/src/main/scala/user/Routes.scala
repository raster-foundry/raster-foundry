package com.azavea.rf.user

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.model.StatusCodes

import com.azavea.rf.utils.Database
import com.azavea.rf.auth.Authentication
import com.azavea.rf.datamodel.latest.schema.tables.UsersRow

/**
  * Routes for users
  */
trait UserRoutes extends Authentication {

  implicit val database: Database
  implicit val ec: ExecutionContext

  def userRoutes = (
    authenticate { user =>
      pathPrefix("api" / "users") {
        pathEndOrSingleSlash {
          get {
            onSuccess(UserService.getUsers) {
              resp => complete(resp)
            }
          } ~
          post {
            entity(as[UsersRowCreate]) {
              newUser =>
              onSuccess(UserService.createUser(newUser)) {
                resp => complete((StatusCodes.Created, resp))
              }
            }
          }
        } ~
        pathPrefix(JavaUUID) { id =>
          pathEndOrSingleSlash {
            get {
              onSuccess(UserService.getUserById(id)) {
                resp => resp match {
                  case Some(user) => complete(user)
                  case _ => complete((StatusCodes.NotFound))
                }
              }
            } ~
            put {
              entity(as[UsersRow]) {
                user =>
                onSuccess(UserService.updateUser(user, id)) {
                  resp => resp match {
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
  )
}
