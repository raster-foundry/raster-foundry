package com.azavea.rf.token

import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.{Authorization, GenericHttpCredentials}
import akka.http.scaladsl.unmarshalling.Unmarshal

import spray.json.DefaultJsonProtocol

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.azavea.rf.datamodel.User
import com.azavea.rf.utils.Config


case class DeviceCredential(id: String, device_name: String)

object TokenJsonProtocol extends SprayJsonSupport
  with DefaultJsonProtocol {

  implicit val deviceCredentialJson = jsonFormat2(DeviceCredential)
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
}
