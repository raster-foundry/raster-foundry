package com.azavea.rf.user

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.model.StatusCodes

import com.azavea.rf.utils.Database
import com.azavea.rf.auth.Authentication

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
                resp => complete(resp)
              }
            } ~
            put {
              entity(as[UsersRowApi]) {
                user =>
                onSuccess(UserService.updateUser(user, id)) {
                  resp => complete((StatusCodes.NoContent))
                }
              }
            }
          }
        }
      }
    }
  )
}
