package com.azavea.rf.api.feed

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.Uri.{Path, Query}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling._

import com.azavea.rf.common.UserErrorHandler

import com.github.blemale.scaffeine.{AsyncLoadingCache, Scaffeine}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

import com.typesafe.scalalogging.LazyLogging
import com.github.blemale.scaffeine.{AsyncLoadingCache, Scaffeine}

trait FeedRoutes extends UserErrorHandler {
  val feedRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { complete { FeedService.getFeed() }}
    }
  }
}

object FeedService extends LazyLogging {
  import com.azavea.rf.api.AkkaSystem._

  val feedCache: AsyncLoadingCache[Int, String] =
    Scaffeine()
      .expireAfterWrite(1.hours)
      .maximumSize(1)
      .buildAsyncFuture((i: Int) => fetchFeed())

  val gidUri = Uri("https://medium.com/m/global-identity?redirectUrl=https://blog.rasterfoundry.com/latest?format=json")
  def fetchFeed(): Future[String] = {
    Http()
      .singleRequest(HttpRequest(method = GET, uri = gidUri))
      .flatMap {
        case HttpResponse(StatusCodes.Found, headers, _, _) =>
          val location: Option[HttpHeader] = headers.find((header) => header.is("location"))
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
                    throw new Exception(s"Error fetching feed: $errCode, $error")
                }
            case _ =>
              throw new Exception("Error fetching feed: Unable to obtain global id for request")
          }
      }
  }

  def getFeed(): Future[String] = {
    feedCache.get(1)
  }
}
