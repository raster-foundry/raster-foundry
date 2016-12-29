package com.azavea.rf.thumbnail

import java.util.UUID

import scala.util.{Success, Failure}

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes

import com.azavea.rf.utils.RfPaginationDirectives

import com.azavea.rf.auth.Authentication
import com.azavea.rf.database.tables.Thumbnails
import com.azavea.rf.database.Database
import com.azavea.rf.datamodel._
import com.azavea.rf.utils.{UserErrorHandler, RouterHelper}


trait ThumbnailRoutes extends Authentication
    with ThumbnailQueryParameterDirective
    with RfPaginationDirectives
    with UserErrorHandler
    with RouterHelper {

  implicit def database: Database

  val thumbnailRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listThumbnails } ~
      post { createThumbnail }
    } ~
    pathPrefix(JavaUUID) { thumbnailId =>
      pathEndOrSingleSlash {
        get { getThumbnail(thumbnailId) } ~
        put { updateThumbnail(thumbnailId) } ~
        delete { deleteThumbnail(thumbnailId) }
      }
    }
  }

  def listThumbnails: Route = authenticate { user =>
    (withPagination & thumbnailSpecificQueryParameters) { (page, thumbnailParams) =>
      complete {
        Thumbnails.listThumbnails(page, thumbnailParams)
      }
    }
  }

  def createThumbnail: Route = authenticate { user =>
    entity(as[Thumbnail.Create]) { newThumbnail =>
      onSuccess(Thumbnails.insertThumbnail(newThumbnail.toThumbnail)) { thumbnail =>
        complete(StatusCodes.Created, thumbnail)
      }
    }
  }

  def getThumbnail(thumbnailId: UUID): Route = authenticate { user =>
    withPagination { page =>
      rejectEmptyResponse {
        complete {
          Thumbnails.getThumbnail(thumbnailId)
        }
      }
    }
  }

  def updateThumbnail(thumbnailId: UUID): Route = authenticate { user =>
    entity(as[Thumbnail]) { updatedThumbnail =>
      onSuccess(Thumbnails.updateThumbnail(updatedThumbnail, thumbnailId)) {
        case 1 => complete(StatusCodes.NoContent)
        case 0 => complete(StatusCodes.NotFound)
        case count => throw new IllegalStateException(
          s"Error updating thumbnail: update result expected to be 1, was $count"
        )
      }
    }
  }

  def deleteThumbnail(thumbnailId: UUID): Route = authenticate { user =>
    onSuccess(Thumbnails.deleteThumbnail(thumbnailId)) {
      case 1 => complete(StatusCodes.NoContent)
      case 0 => complete(StatusCodes.NotFound)
      case count => throw new IllegalStateException(
        s"Error deleting thumbnail: delete result expected to be 1, was $count"
      )
    }
  }
}
