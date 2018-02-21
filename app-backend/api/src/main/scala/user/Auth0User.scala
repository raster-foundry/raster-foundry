package com.azavea.rf.api.user

import com.azavea.rf.datamodel.User
import com.azavea.rf.api.utils.Config
import com.azavea.rf.api.utils.{Auth0Exception, ManagementBearerToken}
import com.azavea.rf.database.UserDao
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import com.azavea.rf.database.filter.Filterables._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, GenericHttpCredentials}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshalling.Marshal
import cats.effect.IO
import com.github.blemale.scaffeine.{AsyncLoadingCache, Scaffeine}
import com.typesafe.scalalogging.LazyLogging
import io.circe._
import io.circe.syntax._
import io.circe.generic.JsonCodec
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Future


@JsonCodec
case class Auth0User(
  email: Option[String], email_verified: Option[Boolean],
  username: Option[String],
  phone_number: Option[String], phone_verified: Option[String],
  user_id: Option[String],
  created_at: Option[String], updated_at: Option[String],
  identities: Option[Json],
  // app_metadata: Option[Json],
  user_metadata: Option[Json],
  picture: Option[String],
  name: Option[String],
  nickname: Option[String],
  // multifactor: Option[Seq[String]],
  // last_ip: Option[String],
  // last_login: Option[String],
  // logins_count: Option[Int],
  // blocked: Option[Boolean],
  given_name: Option[String],
  family_name: Option[String]
)

@JsonCodec
case class UserWithOAuth(
  user: User,
  oauth: Auth0User
)

@JsonCodec
case class Auth0UserUpdate(
  email: Option[String],
  phone_number: Option[String],
  user_metadata: Option[Json],
  username: Option[String]
)

@JsonCodec
case class UserWithOAuthUpdate(
  user: User.Create,
  oauth: Auth0UserUpdate
)

object Auth0UserService extends Config with LazyLogging{
  import com.azavea.rf.api.AkkaSystem._

  val uri = Uri(s"https://$auth0Domain/api/v2/device-credentials")
  val userUri = Uri(s"https://$auth0Domain/api/v2/users")
  val auth0BearerHeader = List(
    Authorization(GenericHttpCredentials("Bearer", auth0Bearer))
  )

  val authBearerTokenCache: AsyncLoadingCache[Int, ManagementBearerToken] =
    Scaffeine()
      .expireAfterWrite(1.hour)
      .maximumSize(1)
      .buildAsyncFuture((i: Int) => getManagementBearerToken())

  def getManagementBearerToken(): Future[ManagementBearerToken] = {
    val bearerTokenUri = Uri(s"https://$auth0Domain/oauth/token")

    val params = FormData(
      "client_id" -> auth0ManagementClientId,
      "client_secret" -> auth0ManagementSecret,
      "audience" -> s"https://$auth0Domain/api/v2/",
      "grant_type" -> "client_credentials"
    ).toEntity

    Http()
      .singleRequest(HttpRequest(
                       method = POST,
                       uri = bearerTokenUri,
                       entity = params
                     ))
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
          Unmarshal(entity).to[ManagementBearerToken]
        case HttpResponse(errCode, _, error, _) =>
          throw new Auth0Exception(errCode, error.toString)
      }
  }

  def getAuth0User(userId: String)(implicit xa: Transactor[IO]) : Future[UserWithOAuth] = {
     val query: Future[Auth0User] = for {
      bearerToken <- authBearerTokenCache.get(1)
      auth0User <- requestAuth0User(userId, bearerToken)
    } yield auth0User
    query.flatMap { auth0User =>
      UserDao.query.filter(fr"id = ${userId}").selectOption.transact(xa).unsafeToFuture().map { user =>
        user match {
          case Some(user: User) =>
            UserWithOAuth(user, auth0User)
          case _ =>
            throw new Auth0Exception(StatusCodes.NotFound, "Unable to find user in database.")
        }
      }
    }
  }

  def requestAuth0User(userId: String, bearerToken: ManagementBearerToken): Future[Auth0User] = {
    val auth0UserBearerHeader = List(
      Authorization(GenericHttpCredentials("Bearer", bearerToken.access_token))
    )
    Http().singleRequest(HttpRequest(
        method = GET,
        uri = s"$userUri/${userId}",
        headers = auth0UserBearerHeader
      ))
      .flatMap {
      case HttpResponse(StatusCodes.OK, _, entity, _) =>
          Unmarshal(entity).to[Auth0User]
      case HttpResponse(StatusCodes.Unauthorized, _, error, _) =>
        if (error.toString.contains("invalid_refresh_token")) {
          throw new IllegalArgumentException("Refresh token not recognized")
        } else {
          throw new Auth0Exception(StatusCodes.Unauthorized, error.toString)
        }
      case HttpResponse(errCode, _, error, _) =>
          logger.info(s"error $error")
        throw new Auth0Exception(errCode, error.toString)
    }
  }

  def updateAuth0User(userId: String, auth0UserUpdate: Auth0UserUpdate): Future[Auth0User] = {
    for {
      bearerToken <- authBearerTokenCache.get(1)
      auth0User <- requestAuth0UserUpdate(userId, auth0UserUpdate, bearerToken)
    } yield auth0User
  }

  def requestAuth0UserUpdate(userId: String, auth0UserUpdate: Auth0UserUpdate, bearerToken: ManagementBearerToken):
      Future[Auth0User] = {
    val auth0UserBearerHeader = List(
      Authorization(GenericHttpCredentials("Bearer", bearerToken.access_token))
    )
    Http().singleRequest(HttpRequest(
                           method = PATCH,
                           uri = s"$userUri/${userId}",
                           headers = auth0UserBearerHeader,
                           entity = HttpEntity(
                             ContentTypes.`application/json`,
                             auth0UserUpdate.asJson.noSpaces
                           )
                         ))
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
          Unmarshal(entity).to[Auth0User]
        case HttpResponse(StatusCodes.ClientError(400), _, error, _) =>
          throw new IllegalArgumentException("Request must specify a valid field to update")
        case HttpResponse(StatusCodes.Unauthorized, _, error, _) =>
          if (error.toString.contains("invalid_refresh_token")) {
            throw new IllegalArgumentException("Refresh token not recognized")
          } else {
            throw new Auth0Exception(StatusCodes.Unauthorized, error.toString)
          }
        case HttpResponse(errCode, _, error, _) =>
          logger.info(s"error $error")
          throw new Auth0Exception(errCode, error.toString)
      }
  }
}
