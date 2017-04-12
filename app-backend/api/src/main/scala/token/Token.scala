package com.azavea.rf.api.token

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.Uri.{Path, Query}
import akka.http.scaladsl.model.headers.{Authorization, GenericHttpCredentials}
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.github.blemale.scaffeine.{AsyncLoadingCache, Scaffeine}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Future

import com.azavea.rf.datamodel.User
import com.azavea.rf.api.utils.Config
import com.azavea.rf.api.utils.{Auth0Exception, ManagementBearerToken}

import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.JsonCodec

// TODO: this sort of case class definition should live in datamodel
@JsonCodec
case class RefreshToken(refresh_token: String)
@JsonCodec
case class DeviceCredential(id: String, device_name: String)
@JsonCodec
case class AuthorizedToken(id_token: String, expires_in: Int, token_type: String)

object TokenService extends Config {

  import com.azavea.rf.api.AkkaSystem._

  val uri = Uri(s"https://$auth0Domain/api/v2/device-credentials")

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

  def listRefreshTokens(user: User): Future[List[DeviceCredential]] = {
    for {
      bearerToken <- authBearerTokenCache.get(1)
      deviceCredentialsList <- requestDeviceTokens(user, bearerToken)
    } yield deviceCredentialsList
  }

  def requestDeviceTokens(user: User, bearerToken: ManagementBearerToken):Future[List[DeviceCredential]] = {
    val params = Query(
      "type" -> "refresh_token",
      "user_id" -> user.id
    )

    Http()
      .singleRequest(HttpRequest(
        method = GET,
        uri = uri.withQuery(params),
        headers = List(
          Authorization(GenericHttpCredentials("Bearer", bearerToken.access_token))
        )
      ))
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
          Unmarshal(entity).to[List[DeviceCredential]]
        case HttpResponse(errCode, _, error, _) =>
          throw new Auth0Exception(errCode, error.toString)
      }
  }

  def getAuthorizedToken(rt: RefreshToken): Future[AuthorizedToken] = {

    val params = FormData(
      "api_type" -> "app",
      "grant_type" -> "urn:ietf:params:oauth:grant-type:jwt-bearer",
      "scope" -> "openid",
      "refresh_token" -> rt.refresh_token,
      "client_id" -> auth0ClientId,
      "target" -> auth0ClientId
    ).toEntity

    Http()
      .singleRequest(HttpRequest(
        method = POST,
        uri = uri.withPath(Path("/delegation")),
        entity = params
      ))
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
          Unmarshal(entity).to[AuthorizedToken]
        case HttpResponse(StatusCodes.Unauthorized, _, error, _) =>
          if (error.toString.contains("invalid_refresh_token")) {
            throw new IllegalArgumentException("Refresh token not recognized")
          } else {
            throw new Auth0Exception(StatusCodes.Unauthorized, error.toString)
          }
        case HttpResponse(errCode, _, error, _) =>
          throw new Auth0Exception(errCode, error.toString)
      }
  }

  def revokeRefreshToken(user: User, deviceId: String): Future[StatusCode] = {
    for {
      bearerToken <- authBearerTokenCache.get(1)
      statusCodeOption <- requestRefreshTokenRevocation(user, deviceId, bearerToken)
    } yield statusCodeOption
  }

  def requestRefreshTokenRevocation(user: User, deviceId: String, bearerToken: ManagementBearerToken): Future[StatusCode] = {

    listRefreshTokens(user).flatMap { deviceCredentials =>
      deviceCredentials.count(dc => dc.id == deviceId) > 0 match {
        case true =>
          Http()
            .singleRequest(HttpRequest(
              method = DELETE,
              uri = s"$uri/$deviceId",
              headers = List(
                Authorization(GenericHttpCredentials("Bearer", bearerToken.access_token))
              )
            ))
            .map {
              case HttpResponse(StatusCodes.NoContent, _, _, _) =>
                StatusCodes.NoContent
              case HttpResponse(errCode, _, error, _) =>
                throw new Auth0Exception(errCode, error.toString)
        }
        case _ => Future(StatusCodes.NotFound)
      }
    }
  }
}
