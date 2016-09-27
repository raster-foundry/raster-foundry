package com.azavea.rf.thumbnail

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling._
import akka.http.scaladsl.model.StatusCodes

import com.lonelyplanet.akka.http.extensions.PaginationDirectives

import com.azavea.rf.auth.Authentication
import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.utils.{Database, UserErrorHandler, RouterHelper}


trait ThumbnailRoutes extends Authentication
    with ThumbnailQueryParameterDirective
    with PaginationDirectives
    with UserErrorHandler
    with RouterHelper {

  implicit def database: Database
  implicit val ec: ExecutionContext

  def thumbnailRoutes: Route = handleExceptions(userExceptionHandler) {
    pathPrefix("api" / "thumbnails") {
      getThumbnail ~
      listThumbnails ~
      createThumbnail ~
      updateThumbnail ~
      deleteThumbnail
    }
  }

  def listThumbnails: Route = anonWithPage { (user, page) =>
    get {
      thumbnailSpecificQueryParameters { thumbnailSpecificQueryParameters =>
        onSuccess(ThumbnailService.getThumbnails(page, thumbnailSpecificQueryParameters)) { thumbs =>
          complete(thumbs)
        }
      }
    }
  }

  def getThumbnail: Route = pathPrefix(JavaUUID) { thumbnailId =>
    anonWithPage { (user, page) =>
      get {
        onSuccess(ThumbnailService.getThumbnail(thumbnailId)) {
          case Some(thumbnail) => complete(thumbnail)
          case _ => complete(StatusCodes.NotFound)
        }
      }
    }
  }

  def createThumbnail: Route = authenticate { user =>
    post {
      entity(as[CreateThumbnail]) { newThumbnail =>
        onSuccess(ThumbnailService.insertThumbnail(newThumbnail.toThumbnail)) {
          case Success(thumbnail) => complete(thumbnail)
          case Failure(_) => complete(StatusCodes.InternalServerError)
        }
      }
    }
  }

  def deleteThumbnail: Route = pathPrefix(JavaUUID) {thumbnailId =>
    authenticate { user =>
      delete {
        onSuccess(ThumbnailService.deleteThumbnail(thumbnailId)) {
          case Success(1) => complete(StatusCodes.NoContent)
          case Success(0) => complete(StatusCodes.NotFound)
          case _ => complete(StatusCodes.InternalServerError)
        }
      }
    }
  }

  def updateThumbnail: Route =  pathPrefix(JavaUUID) {thumbnailId =>
    authenticate { user =>
      put {
        entity(as[ThumbnailsRow]) { updatedThumbnail =>
          onSuccess(ThumbnailService.updateThumbnail(updatedThumbnail, thumbnailId)) {
            case Success(result) => {
              result match {
                case 1 => complete(StatusCodes.NoContent)
                case count: Int => throw new Exception(
                  s"Error updating thumbnail: update result expected to be 1, was $count"
                )
              }
            }
            case Failure(e) => throw e
          }
        }
      }
    }
  }
}
