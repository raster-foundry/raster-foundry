package com.rasterfoundry.api.user

import com.rasterfoundry.api.utils.{
  Auth0Exception,
  Config,
  ManagementBearerToken
}
import com.rasterfoundry.datamodel.User

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, GenericHttpCredentials}
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.github.blemale.scaffeine.{AsyncLoadingCache, Scaffeine}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.syntax._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

import java.net.URLEncoder

@JsonCodec
final case class Auth0User(
    email: Option[String],
    email_verified: Option[Boolean],
    username: Option[String],
    phone_number: Option[String],
    phone_verified: Option[String],
    user_id: Option[String],
    created_at: Option[String],
    updated_at: Option[String],
    identities: Option[Json],
    user_metadata: Option[Json],
    picture: Option[String],
    name: Option[String],
    nickname: Option[String],
    given_name: Option[String],
    family_name: Option[String]
)

@JsonCodec
final case class UserWithOAuth(
    user: User,
    oauth: Auth0User
)

@JsonCodec
final case class PasswordResetTicket(ticket: String)

object UserWithOAuth {
  implicit val encodeUser: Encoder[User] = Encoder.forProduct8(
    "id",
    "name",
    "email",
    "profileImageUri",
    "emailNotifications",
    "visibility",
    "dropboxCredential",
    "planetCredential"
  )(
    u =>
      (
        u.id,
        u.name,
        u.email,
        u.profileImageUri,
        u.emailNotifications,
        u.visibility,
        u.dropboxCredential,
        u.planetCredential
    )
  )
}
@JsonCodec
final case class Auth0UserUpdate(
    email: Option[String],
    phone_number: Option[String],
    user_metadata: Option[Json],
    username: Option[String]
)

@JsonCodec
final case class UserWithOAuthUpdate(
    user: User.Create,
    oauth: Auth0UserUpdate
)
object Auth0Service extends Config with LazyLogging {

  import com.rasterfoundry.api.AkkaSystem._

  val uri = Uri(s"https://$auth0Domain/api/v2/device-credentials")
  val userUri = Uri(s"https://$auth0Domain/api/v2/users")
  val passwordChangeUri = Uri(
    s"https://$auth0Domain/api/v2/tickets/password-change"
  )

  val authBearerTokenCache: AsyncLoadingCache[Int, ManagementBearerToken] =
    Scaffeine()
      .expireAfterWrite(1.hour)
      .maximumSize(1)
      .buildAsyncFuture((_: Int) => getManagementBearerToken())

  private def getBearerHeaders(bearerToken: ManagementBearerToken) = List(
    Authorization(
      GenericHttpCredentials(bearerToken.token_type, bearerToken.access_token)
    )
  )

  private def responseAsAuth0User(response: HttpResponse): Future[Auth0User] = {
    response match {
      case HttpResponse(StatusCodes.OK, _, entity, _) =>
        Unmarshal(entity).to[Auth0User]
      case HttpResponse(StatusCodes.Created, _, entity, _) =>
        Unmarshal(entity).to[Auth0User]
      case HttpResponse(StatusCodes.ClientError(400), _, entity, _) =>
        logger.debug(s"Entity from Auth0 is: $entity")
        throw new IllegalArgumentException(
          "Request must specify a valid field to update"
        )
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

  def getManagementBearerToken(): Future[ManagementBearerToken] = {
    val bearerTokenUri = Uri(s"https://$auth0Domain/oauth/token")

    val params = FormData(
      "client_id" -> auth0ManagementClientId,
      "client_secret" -> auth0ManagementSecret,
      "audience" -> s"https://$auth0Domain/api/v2/",
      "grant_type" -> "client_credentials"
    ).toEntity

    Http()
      .singleRequest(
        HttpRequest(
          method = POST,
          uri = bearerTokenUri,
          entity = params
        )
      )
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
          Unmarshal(entity).to[ManagementBearerToken]
        case HttpResponse(errCode, _, error, _) =>
          throw new Auth0Exception(errCode, error.toString)
      }
  }

  def requestAuth0User(
      userId: String,
      bearerToken: ManagementBearerToken
  ): Future[Auth0User] = {
    val auth0UserBearerHeader = getBearerHeaders(bearerToken)

    Http()
      .singleRequest(
        HttpRequest(
          method = GET,
          uri = s"$userUri/${userId}",
          headers = auth0UserBearerHeader
        )
      )
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

  def updateAuth0User(
      userId: String,
      auth0UserUpdate: Auth0UserUpdate
  ): Future[Auth0User] = {
    for {
      bearerToken <- authBearerTokenCache.get(1)
      auth0User <- requestAuth0UserUpdate(userId, auth0UserUpdate, bearerToken)
    } yield auth0User
  }

  def requestAuth0UserUpdate(
      userId: String,
      auth0UserUpdate: Auth0UserUpdate,
      bearerToken: ManagementBearerToken
  ): Future[Auth0User] = {
    val auth0UserBearerHeader = getBearerHeaders(bearerToken)
    Http()
      .singleRequest(
        HttpRequest(
          method = PATCH,
          uri = s"$userUri/${userId}",
          headers = auth0UserBearerHeader,
          entity = HttpEntity(
            ContentTypes.`application/json`,
            auth0UserUpdate.asJson.noSpaces
          )
        )
      )
      .flatMap { responseAsAuth0User _ }
  }

  // don't need a read method because patch is idempotent
  def addGroundworkMetadata(
      user: User,
      bearerToken: ManagementBearerToken
  ): Future[Unit] = {
    val patch = Map("app_metadata" -> Map("annotateApp" -> true)).asJson
    val managementBearerHeaders = getBearerHeaders(bearerToken)

    Http()
      .singleRequest(
        HttpRequest(
          method = PATCH,
          uri = s"$userUri/${user.id}",
          headers = managementBearerHeaders,
          entity = HttpEntity(
            ContentTypes.`application/json`,
            patch.noSpaces
          )
        )
      )
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, _, _) =>
          Future.successful(())
        case HttpResponse(StatusCodes.ClientError(400), _, entity, _) =>
          logger.debug(s"Entity from Auth0 is: $entity")
          throw new IllegalArgumentException(
            "Request must specify a valid field to update"
          )
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

  def findGroundworkUser(
      email: String,
      bearerToken: ManagementBearerToken
  ): Future[Option[Auth0User]] = {
    val managementBearerHeaders = getBearerHeaders(bearerToken)

    val queryString =
      URLEncoder.encode(s"name:$email* OR email:$email*", "utf-8")
    val fields =
      URLEncoder.encode(
        "user_id,name,email,phone_number,identities,picture,lastLogin,loginsCount,user_metadata,app_metadata,blocked,email_verified",
        "utf-8"
      )

    Http()
      .singleRequest(
        HttpRequest(
          method = GET,
          uri = s"$userUri?q=$queryString&fields=$fields",
          headers = managementBearerHeaders
        )
      ) flatMap {
      case HttpResponse(StatusCodes.OK, _, entity, _) =>
        Unmarshal(entity).to[List[Auth0User]] map { users =>
          logger.debug(s"Returned users: $users")
          users.headOption
        }
      case HttpResponse(_, _, error, _) =>
        logger.debug(s"There was an error: $error")
        Future.successful(None)
    }
  }

  def createGroundworkUser(
      email: String,
      bearerToken: ManagementBearerToken
  ): Future[Auth0User] = {
    val post = Map(
      "connection" -> "Username-Password-Authentication".asJson,
      "email" -> email.asJson,
      "password" -> Random.alphanumeric.take(20).mkString("").asJson,
      "username" -> email.takeWhile(_ != '@').take(15).asJson,
      "app_metadata" -> Map("annotateApp" -> true).asJson
    ).asJson

    val managementBearerHeaders = getBearerHeaders(bearerToken)

    Http()
      .singleRequest(
        HttpRequest(
          method = POST,
          uri = s"$userUri",
          headers = managementBearerHeaders,
          entity = HttpEntity(
            ContentTypes.`application/json`,
            post.noSpaces
          )
        )
      )
      .flatMap { responseAsAuth0User _ }
  }

  def createPasswordChangeTicket(
      bearerToken: ManagementBearerToken,
      resultUrl: String,
      userId: String,
      ttlSeconds: Int = 432000,
      markEmailAsVerified: Boolean = true
  ): Future[PasswordResetTicket] = {
    val post = Map(
      "result_url" -> resultUrl.asJson,
      "user_id" -> userId.asJson,
      "ttl_sec" -> ttlSeconds.asJson,
      "mark_email_as_verified" -> markEmailAsVerified.asJson
    ).asJson

    val managementBearerHeaders = getBearerHeaders(bearerToken)

    Http()
      .singleRequest(
        HttpRequest(
          method = POST,
          uri = s"$passwordChangeUri",
          headers = managementBearerHeaders,
          entity = HttpEntity(
            ContentTypes.`application/json`,
            post.noSpaces
          )
        )
      ) flatMap {
      case HttpResponse(StatusCodes.Created, _, entity, _) =>
        Unmarshal(entity).to[PasswordResetTicket]
      case HttpResponse(_, _, entity, _) =>
        logger.error(s"Error entity from Auth0 is: $entity")
        throw new Exception("Unable to create a password change ticket")
    }
  }
}
