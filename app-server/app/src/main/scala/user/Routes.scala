package com.azavea.rf.user

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatchers.JavaUUID
import akka.http.scaladsl.model.StatusCodes

import com.azavea.rf.datamodel.latest.schema.tables.UsersRow
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
            entity(as[UsersRow]) {
              user =>
              onSuccess(UserService.createUser(user)) {
                resp => complete(StatusCodes.Created, user)
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
              entity(as[UsersRow]) {
                user =>
                onSuccess(UserService.updateUser(user)) {
                  resp => complete(StatusCodes.NoContent)
                }
              }
            }
          }
        }
      }
    }
  )
}
