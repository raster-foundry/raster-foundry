package com.azavea.rf.image

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
  implicit val ec: ExecutionContext

  def imageRoutes: Route = handleExceptions(userExceptionHandler) {
    pathPrefix("api" / "images") {
      listImages ~
      createImages ~
      getImage ~
      updateImage ~
      deleteImage
    }
  }


  def listImages: Route = anonWithPage { (user, page) =>
    get {
      imageQueryParameters { imageParams =>
        onSuccess(Images.listImages(page, imageParams)) { images =>
          complete(images)
        }
      }
    }
  }

  def createImages: Route = authenticate { user =>
    post {
      entity(as[Image.Create]) { newImage =>
        onComplete(Images.insertImage(newImage.toImage(user.id))) {
          case Success(image) => complete(image)
          case Failure(_) => complete(StatusCodes.InternalServerError)
        }
      }
    }
  }

  def getImage: Route = pathPrefix(JavaUUID) {imageId =>
    anonWithSlash { user =>
      get {
        onSuccess(Images.getImage(imageId)) {
          case Some(image) => complete(image)
          case _ => complete(StatusCodes.NotFound)
        }
      }
    }
  }

  def updateImage: Route = pathPrefix(JavaUUID) {imageId =>
    authenticate { user =>
      put {
        entity(as[Image]) { updatedImage =>
          onSuccess(Images.updateImage(updatedImage, imageId, user)) { count =>
            complete(StatusCodes.NoContent)
          }
        }
      }
    }
  }

  def deleteImage: Route = pathPrefix(JavaUUID) {imageId =>
    authenticate { user =>
      delete {
        onSuccess(Images.deleteImage(imageId)) {
          case 1 => complete(StatusCodes.NoContent)
          case 0 => complete(StatusCodes.NotFound)
          case _ => complete(StatusCodes.InternalServerError)
        }
      }
    }
  }
}
