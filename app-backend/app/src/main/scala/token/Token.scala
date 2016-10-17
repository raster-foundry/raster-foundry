package com.azavea.rf.token

import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.Uri.{Path, Query}
import akka.http.scaladsl.model.headers.{Authorization, GenericHttpCredentials}
import akka.http.scaladsl.unmarshalling.Unmarshal

import spray.json.DefaultJsonProtocol

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.azavea.rf.datamodel.User
import com.azavea.rf.utils.Config


case class RefreshToken(refresh_token: String)
case class DeviceCredential(id: String, device_name: String)
case class AuthorizedToken(id_token: String, expires_in: Int, token_type: String)

object TokenJsonProtocol extends SprayJsonSupport
  with DefaultJsonProtocol {

  implicit val refreshTokenJson = jsonFormat1(RefreshToken)
  implicit val deviceCredentialJson = jsonFormat2(DeviceCredential)
  implicit val authorizedTokenJson = jsonFormat3(AuthorizedToken)
}

object TokenService extends Config {

  import TokenJsonProtocol._
  import com.azavea.rf.AkkaSystem._

  val uri = Uri(s"https://$auth0Domain/api/v2/device-credentials")
  val auth0BearerHeader = List(
    Authorization(GenericHttpCredentials("Bearer", auth0Bearer))
  )

  def listRefreshTokens(user: User): Future[List[DeviceCredential]] = {

    val params = Query(
      "type" -> "refresh_token",
      "user_id" -> user.id
    )

    Http()
      .singleRequest(HttpRequest(
        method = GET,
        uri = uri.withQuery(params),
        headers = auth0BearerHeader
      ))
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
          Unmarshal(entity).to[List[DeviceCredential]]
        case HttpResponse(errCode, _, _, _) =>
          throw new Exception(s"Error communicating with Auth0: $errCode")
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
        case HttpResponse(errCode, _, _, _) =>
          throw new Exception(s"Error communicating with Auth0: $errCode")
      }
  }

  def revokeRefreshToken(user: User, deviceId: String): Future[StatusCode] = {

    listRefreshTokens(user).flatMap { deviceCredentials =>
      deviceCredentials.count(dc => dc.id == deviceId) > 0 match {
        case true =>
          Http()
            .singleRequest(HttpRequest(
              method = DELETE,
              uri = s"$uri/$deviceId",
              headers = auth0BearerHeader
            ))
            .map {
              case HttpResponse(StatusCodes.NoContent, _, _, _) =>
                StatusCodes.NoContent
              case HttpResponse(errCode, _, _, _) =>
                throw new Exception(s"Error revoking refresh token: $errCode")
            }
        case _ => Future(StatusCodes.NotFound)
      }
    }
  }
}
