package com.azavea.rf.image

import java.util.UUID

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes

import com.azavea.rf.auth.Authentication
import com.azavea.rf.database.tables.Images
import com.azavea.rf.database.Database
import com.azavea.rf.datamodel._
import com.azavea.rf.utils.RfPaginationDirectives
import com.azavea.rf.utils.{UserErrorHandler, RouterHelper}


trait ImageRoutes extends Authentication
    with ImageQueryParametersDirective
    with RfPaginationDirectives
    with UserErrorHandler
    with RouterHelper {

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
        Images.listImages(page, imageParams, user)
      }
    }
  }

  def createImage: Route = authenticate { user =>
    entity(as[Image.Banded]) { newImage =>
      onSuccess(Images.insertImage(newImage, user)) { image =>
        complete(image)
      }
    }
  }

  def getImage(imageId: UUID): Route = authenticate { user =>
    get {
      rejectEmptyResponse {
        complete {
          Images.getImage(imageId)
        }
      }
    }
  }

  def updateImage(imageId: UUID): Route = authenticate { user =>
    entity(as[Image.WithRelated]) { updatedImage =>
      onSuccess(Images.updateImage(updatedImage, imageId, user)) { count =>
        complete(StatusCodes.NoContent)
      }
    }
  }

  def deleteImage(imageId: UUID): Route = authenticate { user =>
    onSuccess(Images.deleteImage(imageId)) {
      case 1 => complete(StatusCodes.NoContent)
      case 0 => complete(StatusCodes.NotFound)
      case count => throw new IllegalStateException(
        s"Error deleting image: delete result expected to be 1, was $count"
      )
    }
  }
}
