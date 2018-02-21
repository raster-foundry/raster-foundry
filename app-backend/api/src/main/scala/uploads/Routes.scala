package com.azavea.rf.api.uploads

import com.azavea.rf.common.{AWSBatch, Authentication, CommonHandlers, S3, UserErrorHandler}
import com.azavea.rf.datamodel._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import com.lonelyplanet.akka.http.extensions.{PageRequest, PaginationDirectives}
import io.circe._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import java.net.URI
import java.util.UUID

import cats.effect.IO

import scala.concurrent.ExecutionContext.Implicits.global
import doobie.util.transactor.Transactor
import com.azavea.rf.datamodel._
import cats.implicits._
import com.azavea.rf.database.UploadDao
import doobie._
import doobie.implicits._
import doobie.Fragments.in
import doobie.postgres._
import doobie.postgres.implicits._
import com.azavea.rf.database.filter.Filterables._


trait UploadRoutes extends Authentication
    with UploadQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler
    with AWSBatch {
  implicit def xa: Transactor[IO]

  val uploadRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listUploads } ~
      post { createUpload }
    } ~
    pathPrefix(JavaUUID) { uploadId =>
      pathEndOrSingleSlash {
        get { getUpload(uploadId) } ~
        put { updateUpload(uploadId) } ~
        delete { deleteUpload(uploadId) }
      } ~
      pathPrefix("credentials") {
        pathEndOrSingleSlash {
          getUploadCredentials(uploadId)
        }
      }
    }
  }

  def listUploads: Route = authenticate { user =>
    (withPagination & uploadQueryParams) {
      (page: PageRequest, queryParams: UploadQueryParameters) =>
      complete {
        UploadDao.query.filter(queryParams).page(page).transact(xa).unsafeToFuture
      }
    }
  }

  def getUpload(uploadId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        UploadDao.query.filter(fr"id = ${uploadId}").ownerFilter(user).selectOption.transact(xa).unsafeToFuture
      }
    }
  }

  def createUpload: Route = authenticate { user =>
    entity(as[Upload.Create]) { newUpload =>
      authorize(user.isInRootOrSameOrganizationAs(newUpload)) {
        val uploadToInsert = (newUpload.uploadType, newUpload.source) match {
          case (UploadType.S3, Some(source)) => {
            if (newUpload.files.nonEmpty) newUpload
            else {
              val files = listAllowedFilesInS3Source(source)
              if (files.nonEmpty) newUpload.copy(files = files)
              else throw new IllegalStateException("No acceptable files found in the provided source")
            }
          }
          case (UploadType.S3, None) => {
            if (newUpload.files.nonEmpty) newUpload
            else throw new IllegalStateException("S3 upload must specify a source if no files are specified")
          }
          case (UploadType.Planet, None) => {
            if (newUpload.files.nonEmpty) newUpload
            else throw new IllegalStateException("Planet upload must specify some ids")
          }
          case (UploadType.Local, _) => newUpload
          case _ => throw new IllegalStateException("Unsupported import type")
        }

        onSuccess(UploadDao.insert(uploadToInsert, user).transact(xa).unsafeToFuture) { upload =>
          if (upload.uploadStatus == UploadStatus.Uploaded) {
            kickoffSceneImport(upload.id)
          }
          complete((StatusCodes.Created, upload))
        }
      }
    }
  }

  def updateUpload(uploadId: UUID): Route = authenticate { user =>
    entity(as[Upload]) { updateUpload =>
      authorize(user.isInRootOrSameOrganizationAs(updateUpload)) {
        onSuccess {
          val x = for {
            u <- UploadDao.query.filter(fr"id = ${uploadId}").ownerFilter(user).selectOption
            c <- UploadDao.update(updateUpload, uploadId, user)
          } yield {
            (u, c) match {
              case (Some(upload), 1) =>
                if (upload.uploadStatus != UploadStatus.Uploaded &&
                  updateUpload.uploadStatus == UploadStatus.Uploaded
                ) kickoffSceneImport(upload.id)
                StatusCodes.NoContent
              case (_, 0) => StatusCodes.NotFound
              case (_, 1) => StatusCodes.NoContent
              case (_, _) => StatusCodes.NoContent
            }
          }
          x.transact(xa).unsafeToFuture
        }{ s => complete(s) }
      }
    }
  }

  def deleteUpload(uploadId: UUID): Route = authenticate { user =>
    onSuccess(UploadDao.query.filter(fr"id = ${uploadId}").ownerFilter(user).delete.transact(xa).unsafeToFuture) {
      completeSingleOrNotFound
    }
  }

  def getUploadCredentials(uploadId: UUID): Route = authenticate { user =>
    extractTokenHeader { jwt =>
      onSuccess(UploadDao.query.filter(fr"id = ${uploadId}").ownerFilter(user).selectOption.transact(xa).unsafeToFuture) {
        case Some(_) => complete(CredentialsService.getCredentials(user, uploadId, jwt.toString))
        case None => complete(StatusCodes.NotFound)
      }
    }
  }
}
