package com.azavea.rf.api.image

import com.azavea.rf.common.{Authentication, UserErrorHandler}
import com.azavea.rf.database.tables.Images
import com.azavea.rf.database.Database
import com.azavea.rf.datamodel._

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import io.circe._
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.CirceSupport

import java.util.UUID

import io.circe._
import io.circe.generic.auto._

import de.heikoseeberger.akkahttpcirce.CirceSupport

trait ImageRoutes extends Authentication
    with ImageQueryParametersDirective
    with PaginationDirectives
    with UserErrorHandler
    with CirceSupport {

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
      authorize(user.isInRootOrSameOrganizationAs(newImage)) {
        onSuccess(Images.insertImage(newImage, user)) { image =>
          complete(image)
        }
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
      authorize(user.isInRootOrSameOrganizationAs(updatedImage)) {
        onSuccess(Images.updateImage(updatedImage, imageId, user)) { count =>
          complete(StatusCodes.NoContent)
        }
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
