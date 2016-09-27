package com.azavea.rf.image

import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes

import com.lonelyplanet.akka.http.extensions.PaginationDirectives

import com.azavea.rf.auth.Authentication
import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.utils.{Database, UserErrorHandler, RouterHelper}


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
        onSuccess(ImageService.listImages(page, imageParams)) { images =>
          complete(images)
        }
      }
    }
  }

  def createImages: Route = authenticate { user =>
    post {
      entity(as[CreateImage]) { newImage =>
        onSuccess(ImageService.insertImage(newImage.toImage(user.id))) {
          case Success(image) => complete(image)
          case Failure(_) => complete(StatusCodes.InternalServerError)
        }
      }
    }
  }

  def getImage: Route = pathPrefix(JavaUUID) {imageId =>
    anonWithSlash { user =>
      get {
        onSuccess(ImageService.getImage(imageId)) {
          case Some(image) => complete(image)
          case _ => complete(StatusCodes.NotFound)
        }
      }
    }
  }

  def updateImage: Route = pathPrefix(JavaUUID) {imageId =>
    authenticate { user =>
      put {
        entity(as[ImagesRow]) { updatedImage =>
          onSuccess(ImageService.updateImage(updatedImage, imageId, user)) {
            case Success(result) => {
              result match {
                case 1 => complete(StatusCodes.NoContent)
                case count: Int => throw new Exception(
                  s"Error updating image: update result expected to be 1, was $count"
                )
              }
            }
            case Failure(e) => throw e
          }
        }
      }
    }
  }

  def deleteImage: Route = pathPrefix(JavaUUID) {imageId =>
    authenticate { user =>
      delete {
        onSuccess(ImageService.deleteImage(imageId)) {
          case 1 => complete(StatusCodes.NoContent)
          case 0 => complete(StatusCodes.NotFound)
          case _ => complete(StatusCodes.InternalServerError)
        }
      }
    }
  }
}
