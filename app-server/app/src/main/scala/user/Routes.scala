package com.azavea.rf.user

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatchers.JavaUUID
import akka.http.scaladsl.model.StatusCodes

import com.azavea.rf.datamodel.latest.schema.tables.UsersRow

import com.azavea.rf.utils.Database


/**
  * Routes for users
  */
trait UserRoutes {
  def userRoutes()(implicit db:Database, ec:ExecutionContext) = (
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
            onSuccess(UserService.createUser(db, ec, user)) {
              resp => complete((StatusCodes.Created, user))
            }
          }
        }
      } ~
        pathPrefix(JavaUUID) { id =>
          pathEndOrSingleSlash {
            get {
              onSuccess(UserService.getUserById(db, ec, id)) {
                resp => complete(resp)
              }
            } ~
              put {
                entity(as[UsersRow]) {
                  user =>
                  onSuccess(UserService.updateUser(db, ec, user)) {
                    resp => complete((StatusCodes.NoContent))
                  }
                }
              }
          }
        }
    }
  )
}
