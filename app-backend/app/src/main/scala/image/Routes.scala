package com.azavea.rf.image

import java.util.UUID

import com.azavea.rf.datamodel.Image

import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes

import com.lonelyplanet.akka.http.extensions.PaginationDirectives

import com.azavea.rf.auth.Authentication
import com.azavea.rf.database.tables._
import com.azavea.rf.database.Database
import com.azavea.rf.utils.{UserErrorHandler, RouterHelper}


trait ImageRoutes extends Authentication
    with ImageQueryParametersDirective
    with PaginationDirectives
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


  def listImages: Route = authenticateAndAllowAnonymous { user =>
    (withPagination & imageQueryParameters) { (page, imageParams) =>
      complete {
        Images.listImages(page, imageParams)
      }
    }
  }

  def createImage: Route = authenticate { user =>
    entity(as[Image.Create]) { newImage =>
      onSuccess(Images.insertImage(newImage.toImage(user.id))) { image =>
        complete(StatusCodes.Created, image)
      }
    }
  }

  def getImage(imageId: UUID): Route = authenticateAndAllowAnonymous { user =>
    get {
      rejectEmptyResponse {
        complete {
          Images.getImage(imageId)
        }
      }
    }
  }

  def updateImage(imageId: UUID): Route = authenticate { user =>
    entity(as[Image]) { updatedImage =>
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
