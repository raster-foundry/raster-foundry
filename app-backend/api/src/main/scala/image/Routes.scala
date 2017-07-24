package com.azavea.rf.api.image

import com.azavea.rf.common.{Authentication, UserErrorHandler, CommonHandlers}
import com.azavea.rf.database.tables.Images
import com.azavea.rf.database.Database
import com.azavea.rf.datamodel._

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import io.circe._
import de.heikoseeberger.akkahttpcirce.CirceSupport

import java.util.UUID

import io.circe._

import de.heikoseeberger.akkahttpcirce.CirceSupport

trait ImageRoutes extends Authentication
    with ImageQueryParametersDirective
    with PaginationDirectives
    with CommonHandlers
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
          complete((StatusCodes.Created, image))
        }
      }
    }
  }

  def getImage(imageId: UUID): Route = authenticate { user =>
    get {
      rejectEmptyResponse {
        complete {
          Images.getImage(imageId, user)
        }
      }
    }
  }

  def updateImage(imageId: UUID): Route = authenticate { user =>
    entity(as[Image.WithRelated]) { updatedImage =>
      authorize(user.isInRootOrSameOrganizationAs(updatedImage)) {
        onSuccess(Images.updateImage(updatedImage, imageId, user)) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteImage(imageId: UUID): Route = authenticate { user =>
    onSuccess(Images.deleteImage(imageId, user)) {
      completeSingleOrNotFound
    }
  }
}
