package com.azavea.rf.image

import java.util.UUID

import scala.concurrent.Future

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes

import com.lonelyplanet.akka.http.extensions.PaginationDirectives

import com.azavea.rf.common.{Authentication, UserErrorHandler}
import com.azavea.rf.database.tables.Images
import com.azavea.rf.database.{Database, ActionRunner}
import com.azavea.rf.datamodel._


trait ImageRoutes extends Authentication
    with ImageQueryParametersDirective
    with PaginationDirectives
    with UserErrorHandler
    with ActionRunner {

  implicit def database: Database

  val imageRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listImages } ~
      post { createImage }
    } ~
    pathPrefix(JavaUUID) { imageId =>
      get { getImage(imageId) } ~
      put { updateImage(imageId) } ~
      delete { deleteImage(imageId) }
    }
  }


  def listImages: Route = authenticate { user =>
    (withPagination & imageQueryParameters) { (page, imageParams) =>
      complete {
        withRelatedSeq1(Images.listImages(page, imageParams, user),
                        page.offset, page.limit):
            Future[PaginatedResponse[Image.WithRelated]]
      }
    }
  }

  def createImage: Route = authenticate { user =>
    entity(as[Image.Banded]) { newImage =>
      onSuccess(withRelatedSingle1(Images.insertImage(newImage, user)): Future[Image.WithRelated]) {
        image: Image.WithRelated => complete(image)
      }
    }
  }

  def getImage(imageId: UUID): Route = authenticate { user =>
    get {
      rejectEmptyResponse {
        complete {
          withRelatedOption1(Images.getImage(imageId)):
              Future[Option[Image.WithRelated]]
        }
      }
    }
  }

  def updateImage(imageId: UUID): Route = authenticate { user =>
    entity(as[Image.WithRelated]) { updatedImage =>
      onSuccess(update(Images.updateImage(updatedImage, imageId, user))) { count =>
        complete(StatusCodes.NoContent)
      }
    }
  }

  def deleteImage(imageId: UUID): Route = authenticate { user =>
    onSuccess(drop(Images.deleteImage(imageId))) {
      case 1 => complete(StatusCodes.NoContent)
      case 0 => complete(StatusCodes.NotFound)
      case count => throw new IllegalStateException(
        s"Error deleting image: delete result expected to be 1, was $count"
      )
    }
  }
}
