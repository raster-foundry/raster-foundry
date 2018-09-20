package com.azavea.rf.api.user

import java.net.URLDecoder

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import cats.effect.IO
import com.azavea.rf.api.utils.queryparams.QueryParametersCommon
import com.azavea.rf.authentication.Authentication
import com.azavea.rf.common.{CommonHandlers, UserErrorHandler}
import com.azavea.rf.database._
import com.azavea.rf.datamodel._
import com.dropbox.core.{DbxAppInfo, DbxRequestConfig, DbxWebAuth}
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor

import scala.collection.JavaConverters._

/**
  * Routes for users
  */
trait UserRoutes
    extends Authentication
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler
    with QueryParametersCommon
    with LazyLogging {

  implicit val xa: Transactor[IO]

  val userRoutes: Route = handleExceptions(userExceptionHandler) {
    pathPrefix("me") {
      pathPrefix("teams") {
        pathEndOrSingleSlash {
          get { getUserTeams }
        }
      } ~
        pathPrefix("roles") {
          get { getUserRoles }
        } ~
        pathEndOrSingleSlash {
          get { getDbOwnUser } ~
            patch { updateAuth0User } ~
            put { updateOwnUser }
        }
    } ~
      pathPrefix("dropbox-setup") {
        pathEndOrSingleSlash {
          post { getDropboxAccessToken }
        }
      } ~
      pathPrefix("search") {
        pathEndOrSingleSlash {
          get { searchUsers }
        }
      } ~
      pathPrefix(Segment) { authIdEncoded =>
        pathEndOrSingleSlash {
          get { getUserByEncodedAuthId(authIdEncoded) } ~
            put { updateUserByEncodedAuthId(authIdEncoded) }
        }
      }
  }

  def updateOwnUser: Route = authenticate { user =>
    entity(as[User]) { userToUpdate =>
      if (userToUpdate.id == user.id) {
        onSuccess(
          UserDao.updateOwnUser(userToUpdate).transact(xa).unsafeToFuture()) {
          completeSingleOrNotFound
        }
      } else {
        complete(StatusCodes.NotFound)
      }
    }
  }

  def getDbOwnUser: Route = authenticate { user =>
    complete(UserDao.unsafeGetUserById(user.id).transact(xa).unsafeToFuture())
  }

  def updateAuth0User: Route = authenticate { user =>
    entity(as[Auth0UserUpdate]) { userUpdate =>
      complete {
        Auth0UserService.updateAuth0User(user.id, userUpdate)
      }
    }
  }

  def getDropboxAccessToken: Route = authenticate { user =>
    entity(as[DropboxAuthRequest]) { dbxAuthRequest =>
      val redirectURI = dbxAuthRequest.redirectURI
      val (dbxKey, dbxSecret) =
        (sys.env.get("DROPBOX_KEY"), sys.env.get("DROPBOX_SECRET")) match {
          case (Some(key), Some(secret)) => (key, secret)
          case _ =>
            throw new RuntimeException(
              "App dropbox credentials must be configured")
        }
      val dbxConfig = new DbxRequestConfig("raster-foundry-authorizer")
      val appInfo = new DbxAppInfo(dbxKey, dbxSecret)
      val webAuth = new DbxWebAuth(dbxConfig, appInfo)
      val session = new DummySessionStore()
      val queryParams = Map[String, Array[String]](
        "code" -> Array(dbxAuthRequest.authorizationCode),
        "state" -> Array(session.get)
      ).asJava
      val authFinish = webAuth.finishFromRedirect(
        dbxAuthRequest.redirectURI,
        session,
        queryParams
      )
      logger.debug("Auth finish from Dropbox successful")
      complete(
        UserDao
          .storeDropboxAccessToken(
            user.id,
            Credential.fromString(authFinish.getAccessToken))
          .transact(xa)
          .unsafeToFuture()
      )
    }
  }

  def getUserByEncodedAuthId(authIdEncoded: String): Route = authenticate {
    user =>
      rejectEmptyResponse {
        val authId = URLDecoder.decode(authIdEncoded, "US_ASCII")
        if (user.id == authId) {
          complete(
            UserDao.unsafeGetUserById(authId).transact(xa).unsafeToFuture())
        } else if (user.id != authId) {
          complete(
            UserDao
              .unsafeGetUserById(authId, Some(false))
              .transact(xa)
              .unsafeToFuture())
        } else {
          complete(StatusCodes.NotFound)
        }
      }
  }

  def getUserTeams: Route = authenticate { user =>
    complete { TeamDao.teamsForUser(user).transact(xa).unsafeToFuture }
  }
  def updateUserByEncodedAuthId(authIdEncoded: String): Route =
    authenticateSuperUser { root =>
      entity(as[User]) { updatedUser =>
        onSuccess(
          UserDao
            .updateUser(updatedUser, authIdEncoded)
            .transact(xa)
            .unsafeToFuture()) {
          completeSingleOrNotFound
        }
      }
    }

  def getUserRoles: Route = authenticate { user =>
    complete {
      UserGroupRoleDao.listByUser(user).transact(xa).unsafeToFuture()
    }
  }

  def searchUsers: Route = authenticate { user =>
    searchParams { (searchParams) =>
      complete {
        UserDao.searchUsers(user, searchParams).transact(xa).unsafeToFuture
      }
    }
  }
}
