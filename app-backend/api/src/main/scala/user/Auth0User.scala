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
import better.files._
import cats.data.EitherT
import cats.implicits._
import com.github.blemale.scaffeine.{AsyncLoadingCache, Scaffeine}
import com.github.t3hnar.bcrypt._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.syntax._
import sttp.client3._
import sttp.client3.akkahttp._
import sttp.client3.circe._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

import java.net.URLEncoder
import java.util.UUID

sealed trait BulkCreateError {
  val error: String
}
case class BulkJobSubmitError(error: String) extends BulkCreateError
case class BulkJobProcessTimeout(error: String) extends BulkCreateError
case class BulkJobRequestUsersError(error: String) extends BulkCreateError

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
final case class Auth0UserBulkCreate(
    username: String,
    email: String,
    email_verified: Boolean,
    password_hash: String,
    app_metadata: Map[String, String]
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
  )(u =>
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

  val sttpBackend = AkkaHttpBackend()

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

  private def getBearerHeaders(bearerToken: ManagementBearerToken) =
    List(
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
        logger.error(s"Entity from Auth0 is: $entity")
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
    logger.info("About to make patch request")
    val request = Http()
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
    request
      .flatMap { resp =>
        resp.entity.discardBytes()
        logger.info(s"Patch response was:\n$resp")
        responseAsAuth0User(resp)
      }
  }

  // don't need a read method because patch is idempotent
  def addUserMetadata(
      userId: String,
      bearerToken: ManagementBearerToken,
      metaData: Json
  ): Future[Unit] = {
    val managementBearerHeaders = getBearerHeaders(bearerToken)

    Http()
      .singleRequest(
        HttpRequest(
          method = PATCH,
          uri = s"$userUri/${userId}",
          headers = managementBearerHeaders,
          entity = HttpEntity(
            ContentTypes.`application/json`,
            metaData.noSpaces
          )
        )
      )
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, _, _) =>
          Future.successful(())
        case HttpResponse(StatusCodes.ClientError(400), _, entity, _) =>
          logger.error(s"Entity from Auth0 is: $entity")
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
      "connection" -> auth0GroundworkConnectionName.asJson,
      "email" -> email.asJson,
      "password" -> Random.alphanumeric.take(20).mkString("").asJson,
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
      .flatMap { resp =>
        logger.info(s"Post response from auth0 was:\n$resp")
        responseAsAuth0User(resp)
      }
  }

  @JsonCodec
  final case class ImportResponse(status: String, id: String)

  @JsonCodec
  final case class Auth0JobStatus(status: String)

  def waitForJobToComplete(
      jobId: String,
      numTries: Int,
      bearerToken: ManagementBearerToken
  ): Future[Either[BulkCreateError, String]] = {
    if (numTries > 15) {
      val e =
        BulkJobProcessTimeout("Exhausted tries when bulk creating users")
      logger.error(e.error)
      Future.successful(Left(e))
    } else {
      // Wait one second before requesting to not spam Auth0
      Thread.sleep(1000)
      basicRequest
        .header("Authorization", s"Bearer ${bearerToken.access_token}")
        .get(uri"https://$auth0Domain/api/v2/jobs/$jobId")
        .response(asJson[Auth0JobStatus])
        .send(sttpBackend)
        .flatMap { response =>
          response.body match {
            case Left(_) => {
              val e = BulkJobSubmitError(
                s"Error Processing: ${response.code} - ${response.statusText}"
              )
              logger.error(e.error)
              Future.successful(Left(e))
            }
            case Right(status) =>
              status.status match {
                case "completed" =>
                  Future.successful(Right(jobId))
                case _ =>
                  waitForJobToComplete(
                    jobId,
                    numTries + 1,
                    bearerToken
                  )
              }
          }
        }
    }
  }

  def getBulkCreatedUsers(
      bulkCreateId: String
  ): Future[Either[BulkCreateError, List[Auth0User]]] = {
    val q = s"""app_metadata.bulkCreateId:"$bulkCreateId""""
    val requestUri = uri"https://$auth0Domain/api/v2/users?q=$q&per_page=100"
    for {
      managementToken <- authBearerTokenCache.get(1)
      authHeader = s"Bearer ${managementToken.access_token}"
      response <-
        basicRequest
          .header("Authorization", authHeader)
          .get(requestUri)
          .response(asJson[List[Auth0User]])
          .send(sttpBackend)
          .map { r =>
            r.body.leftMap { _ =>
              val e = BulkJobRequestUsersError(
                s"Status Code: ${r.code}, Status Text: ${r.statusText} for $requestUri"
              )
              logger.error(e.error)
              e
            }
          }
    } yield response

  }

  def bulkCreateUsers(
      userIds: List[String]
  ): Future[Either[BulkCreateError, List[Auth0User]]] = {
    val bulkCreateId = UUID.randomUUID()
    val usersToCreate = userIds
      .map(uid =>
        Auth0UserBulkCreate(
          uid,
          s"$uid@${auth0AnonymizedConnectionName}.com",
          true,
          uid.bcrypt(10),
          Map("bulkCreateId" -> bulkCreateId.toString)
        )
      )
      .asJson
      .noSpaces
    val tempFile = File.newTemporaryFile()
    tempFile.write(usersToCreate)
    val bulkCreateProgram = for {
      managementToken <- EitherT.right(authBearerTokenCache.get(1))
      createResponse <- EitherT {
        basicRequest
          .header("Authorization", s"Bearer ${managementToken.access_token}")
          .multipartBody(
            multipart("upsert", true),
            multipart("connection_id", auth0AnonymizedConnectionId),
            multipartFile("users", tempFile.path),
            multipart("send_completion_email", false)
          )
          .response(asJson[ImportResponse])
          .post(uri"https://$auth0Domain/api/v2/jobs/users-imports")
          .send(sttpBackend)
          .map { r =>
            r.body match {
              case Left(_) => {
                val e = BulkJobSubmitError(
                  s"Status Code: ${r.code}, Status Text: ${r.statusText}"
                )
                logger.error(e.error)
                Left(e)
              }
              case Right(importResponse) => Right(importResponse)
            }
          }
      }
      _ <- EitherT(
        waitForJobToComplete(createResponse.id, 0, managementToken)
      )
      users <- EitherT(getBulkCreatedUsers(bulkCreateId.toString))
    } yield users
    bulkCreateProgram.value
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
