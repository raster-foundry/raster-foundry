package com.azavea.rf.api.user

import java.net.URLDecoder

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes

import com.lonelyplanet.akka.http.extensions.PaginationDirectives

import com.dropbox.core.{DbxAppInfo, DbxRequestConfig, DbxWebAuth}

import com.azavea.rf.common.{Authentication, UserErrorHandler, CommonHandlers}
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.Users
import com.azavea.rf.datamodel._
import io.circe._
import de.heikoseeberger.akkahttpcirce.CirceSupport._

import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


/**
  * Routes for users
  */
trait UserRoutes extends Authentication
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler
    with LazyLogging {

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
      } ~
      pathPrefix("dropbox-setup") {
        pathEndOrSingleSlash {
          post { getDropboxAccessToken(authIdEncoded) }
        }
      }
    }
  }

  def listUsers: Route = authenticateRootMember { user =>
    withPagination { page =>
      complete {
        Users.listUsers(page)
      }
    }
  }

  def createUser: Route = authenticateRootMember { root =>
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

  def getDropboxAccessToken(authIdEncoded: String): Route = {
    val userId = URLDecoder.decode(authIdEncoded)
    entity(as[DropboxAuthRequest]) { dbxAuthRequest =>
      val code = dbxAuthRequest.authorizationCode
      val (dbxKey, dbxSecret) =
        (sys.env.get("DROPBOX_KEY"), sys.env.get("DROPBOX_SECRET")) match {
          case (Some(key), Some(secret)) => (key, secret)
          case _ => throw new RuntimeException("App dropbox credentials must be configured")
        }
      val dbxConfig = new DbxRequestConfig("raster-foundry-authorizer")
      val appInfo = new DbxAppInfo(dbxKey, dbxSecret)
      val webAuth = new DbxWebAuth(dbxConfig, appInfo)
      val authFinish = webAuth.finishFromCode(code)
      Users.storeDropboxAccessToken(userId, authFinish.getAccessToken)
      complete(StatusCodes.OK)
    }
  }

  def getUserByEncodedAuthId(authIdEncoded: String): Route = authenticate { user =>
    rejectEmptyResponse {
      val authId = URLDecoder.decode(authIdEncoded, "US_ASCII")
      if (user.isInRootOrganization || user.id == authId) {
        complete(Users.getUserById(authId))
      } else {
        complete(StatusCodes.NotFound)
      }
    }
  }

  def updateUserByEncodedAuthId(authIdEncoded: String): Route = authenticateRootMember { root =>
    entity(as[User]) { updatedUser =>
      onSuccess(Users.updateUser(updatedUser, authIdEncoded)) {
        completeSingleOrNotFound
      }
    }
  }
}
