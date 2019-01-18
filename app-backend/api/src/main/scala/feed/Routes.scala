package com.rasterfoundry.api.feed

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.Uri.{Path, Query}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling._
import com.lonelyplanet.akka.http.extensions.PageRequest
import com.typesafe.scalalogging.LazyLogging
import com.github.blemale.scaffeine.{AsyncLoadingCache, Scaffeine}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import com.rasterfoundry.akkautil.UserErrorHandler
import com.rasterfoundry.api.utils.queryparams._
import com.rasterfoundry.datamodel._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

trait FeedRoutes extends UserErrorHandler with FeedQueryParametersDirective {
  val feedRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get {
        (feedQueryParameters) { (feedParams) =>
          complete { FeedService.getFeed(feedParams) }
        }
      }
    }
  }
}

trait FeedQueryParametersDirective extends QueryParametersCommon {
  val feedQueryParameters = parameters(
    (
      'source.as[String].?
    )
  ).as(FeedQueryParameters.apply _)
}

object FeedService extends LazyLogging {
  import com.rasterfoundry.api.AkkaSystem._

  val feedCache: AsyncLoadingCache[String, String] =
    Scaffeine()
      .expireAfterWrite(1.hours)
      .maximumSize(100) // adjust as needed
      .buildAsyncFuture((source: String) => fetchFeed(source))

  val defaultRedirectUri = "https://blog.rasterfoundry.com/latest?format=json"
  val gidUri = "https://medium.com/m/global-identity"
  def fetchFeed(source: String): Future[String] = {
    source contains "medium.com" match {
      case true =>
        val uri = source
        Http()
          .singleRequest(
            HttpRequest(
              method = GET,
              uri = uri,
              entity = HttpEntity(ContentTypes.`application/json`, "")
            ))
          .flatMap {
            case HttpResponse(StatusCodes.OK, _, entity, _) =>
              Unmarshal(entity).to[String]
            case HttpResponse(errCode, _, error, _) =>
              throw new Exception(s"Error fetching feed: $errCode, $error")
          }
      case false =>
        val uri = Uri(gidUri).withRawQueryString("redirectUrl=" + source)
        Http()
          .singleRequest(HttpRequest(method = GET, uri = uri))
          .flatMap {
            case HttpResponse(StatusCodes.Found, headers, _, _) =>
              val location: Option[HttpHeader] =
                headers.find((header) => header.is("location"))
              location match {
                case Some(loc: HttpHeader) =>
                  Http()
                    .singleRequest(
                      HttpRequest(
                        method = GET,
                        uri = loc.value,
                        entity = HttpEntity(ContentTypes.`application/json`, "")
                      )
                    )
                    .flatMap {
                      case HttpResponse(StatusCodes.OK, _, entity, _) =>
                        Unmarshal(entity).to[String]
                      case HttpResponse(errCode, _, error, _) =>
                        throw new Exception(
                          s"Error fetching feed: $errCode, $error")
                    }
                case _ =>
                  throw new Exception(
                    "Error fetching feed: Unable to obtain global id for request")
              }
          }
    }
  }

  def getFeed(feedParams: FeedQueryParameters): Future[String] = {
    feedCache.get(feedParams.source.getOrElse(defaultRedirectUri))
  }
}
