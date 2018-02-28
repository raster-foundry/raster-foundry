package com.azavea.rf.api.image

import com.azavea.rf.common.{Authentication, CommonHandlers, UserErrorHandler}
import com.azavea.rf.database.ImageDao
import com.azavea.rf.datamodel._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import io.circe._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import java.util.UUID

import cats.effect.IO
import doobie.util.transactor.Transactor
import com.azavea.rf.database.filter.Filterables._
import com.azavea.rf.datamodel._
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.Fragments.in
import doobie.postgres._
import doobie.postgres.implicits._



trait ImageRoutes extends Authentication
    with ImageQueryParametersDirective
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler {

  implicit def xa: Transactor[IO]

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
        ImageDao.query.filter(imageParams).filter(user).page(page).transact(xa).unsafeToFuture
      }
    }
  }

  def createImage: Route = authenticate { user =>
    entity(as[Image.Banded]) { newImage =>
      authorize(user.isInRootOrSameOrganizationAs(newImage)) {
        onSuccess(ImageDao.insertImage(newImage, user).transact(xa).unsafeToFuture) { image =>
          complete((StatusCodes.Created, image))
        }
      }
    }
  }

  def getImage(imageId: UUID): Route = authenticate { user =>
    get {
      rejectEmptyResponse {
        complete {
          ImageDao.query.filter(user).filter(imageId).selectOption.transact(xa).unsafeToFuture
        }
      }
    }
  }

  def updateImage(imageId: UUID): Route = authenticate { user =>
    entity(as[Image.WithRelated]) { updatedImage =>
      authorize(user.isInRootOrSameOrganizationAs(updatedImage)) {
        onSuccess(ImageDao.updateImage(updatedImage.toImage, imageId, user).transact(xa).unsafeToFuture) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteImage(imageId: UUID): Route = authenticate { user =>
    onSuccess(
      ImageDao.deleteImage(imageId, user)
        .transact(xa).unsafeToFuture
    ) {
      completeSingleOrNotFound
    }
  }
}
