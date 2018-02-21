package com.azavea.rf.api.user

import java.net.URLDecoder

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import cats.effect.IO
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import com.dropbox.core.{DbxAppInfo, DbxRequestConfig, DbxWebAuth}
import com.azavea.rf.common.{Authentication, CommonHandlers, UserErrorHandler}
import com.azavea.rf.database.UserDao
import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.datamodel._
import io.circe._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import com.typesafe.scalalogging.LazyLogging
import doobie.util.transactor.Transactor

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import doobie._
import doobie.implicits._
import doobie.Fragments.in
import doobie.postgres._
import doobie.postgres.implicits._


/**
  * Routes for users
  */
trait UserRoutes extends Authentication
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler
    with LazyLogging {

  implicit def xa: Transactor[IO]

  val userRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listUsers } ~
      post { createUser }
    } ~
    pathPrefix("me") {
      get { getAuth0User } ~
      patch { updateAuth0User } ~
      put { updateOwnUser }
    }  ~
    pathPrefix("dropbox-setup") {
      pathEndOrSingleSlash {
        post { getDropboxAccessToken }
      }
    } ~
    pathPrefix(Segment) { authIdEncoded =>
      pathEndOrSingleSlash {
        get { getUserByEncodedAuthId(authIdEncoded) } ~
        put { updateUserByEncodedAuthId(authIdEncoded) }
      }
    }
  }

  def listUsers: Route = authenticateRootMember { user =>
    withPagination { page =>
      complete {
        UserDao.query.page(page).transact(xa).unsafeToFuture
      }
    }
  }

  def createUser: Route = authenticateRootMember { root =>
    entity(as[User.Create]) { newUser =>
      onSuccess(UserDao.create(newUser).transact(xa).unsafeToFuture()) { createdUser =>
        onSuccess(UserDao.query.filter(fr"id = ${createdUser.id}").selectOption.transact(xa).unsafeToFuture()) {
          case Some(user) => complete((StatusCodes.Created, user))
          case None => throw new IllegalStateException("Unable to create user")
        }
      }
    }
  }

  def updateOwnUser: Route = authenticate { user =>
    entity(as[User]) { updatedUser =>
      onSuccess(UserDao.updateSelf(user, updatedUser).transact(xa).unsafeToFuture()) {
        completeSingleOrNotFound
      }
    }
  }

  def getAuth0User: Route = authenticate { user =>
    complete {
      Auth0UserService.getAuth0User(user.id)
    }
  }

  def updateAuth0User: Route = authenticate {
    user =>
    entity(as[Auth0UserUpdate]) { userUpdate =>
      complete {
        Auth0UserService.updateAuth0User(user.id, userUpdate)
      }
    }
  }

  def getDropboxAccessToken: Route = authenticate {
    user =>
    entity(as[DropboxAuthRequest]) { dbxAuthRequest =>
      val redirectURI = dbxAuthRequest.redirectURI
      val (dbxKey, dbxSecret) =
        (sys.env.get("DROPBOX_KEY"), sys.env.get("DROPBOX_SECRET")) match {
          case (Some(key), Some(secret)) => (key, secret)
          case _ => throw new RuntimeException("App dropbox credentials must be configured")
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
        dbxAuthRequest.redirectURI, session, queryParams
      )
      logger.debug("Auth finish from Dropbox successful")
      complete(UserDao.storeDropboxAccessToken(user.id, authFinish.getAccessToken).transact(xa).unsafeToFuture())
    }
  }

  def getUserByEncodedAuthId(authIdEncoded: String): Route = authenticate { user =>
    rejectEmptyResponse {
      val authId = URLDecoder.decode(authIdEncoded, "US_ASCII")
      if (user.isInRootOrganization || user.id == authId) {
        complete(UserDao.query.filter(fr"id = ${authId}").select.transact(xa).unsafeToFuture())
      } else {
        complete(StatusCodes.NotFound)
      }
    }
  }

  def updateUserByEncodedAuthId(authIdEncoded: String): Route = authenticateRootMember { root =>
    entity(as[User]) { updatedUser =>
      onSuccess(UserDao.updateUser(updatedUser, authIdEncoded).transact(xa).unsafeToFuture()) {
        completeSingleOrNotFound
      }
    }
  }
}
