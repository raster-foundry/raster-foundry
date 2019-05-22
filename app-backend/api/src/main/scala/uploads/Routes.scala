package com.rasterfoundry.api.uploads

import java.util.UUID

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.HttpChallenge
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Route}
import cats.effect.IO
import com.rasterfoundry.akkautil._
import com.rasterfoundry.common.AWSBatch
import com.rasterfoundry.database.UploadDao
import com.rasterfoundry.database.filter.Filterables._
import com.rasterfoundry.datamodel._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie.implicits._
import doobie.util.transactor.Transactor

trait UploadRoutes
    extends Authentication
    with UploadQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler
    with AWSBatch {
  val xa: Transactor[IO]

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
          UploadDao.query
            .filter(user)
            .filter(queryParams)
            .page(page)
            .transact(xa)
            .unsafeToFuture
        }
    }
  }

  def getUpload(uploadId: UUID): Route = authenticate { user =>
    authorizeAsync {
      UploadDao.query
        .ownedByOrSuperUser(user, uploadId)
        .exists
        .transact(xa)
        .unsafeToFuture
    } {
      rejectEmptyResponse {
        complete {
          UploadDao.query
            .filter(uploadId)
            .selectOption
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def createUpload: Route = authenticate { user =>
    entity(as[Upload.Create]) { newUpload =>
      val uploadToInsert = (newUpload.uploadType, newUpload.source, newUpload.fileType) match {
        case (UploadType.S3, None, FileType.NonSpatial) => {
          if (newUpload.files.nonEmpty) newUpload
          else
            throw new IllegalStateException(
              "S3 upload must specify a source if no files are specified")
        }
        case (UploadType.S3, Some(source), _) => {
          if (newUpload.files.nonEmpty) newUpload
          else {
            val files = listAllowedFilesInS3Source(source)
            if (files.nonEmpty) newUpload.copy(files = files)
            else
              throw new IllegalStateException(
                "No acceptable files found in the provided source")
          }
        }
        case (UploadType.S3, None, _) => {
          if (newUpload.files.nonEmpty) newUpload
          else
            throw new IllegalStateException(
              "S3 upload must specify a source if no files are specified")
        }
        case (_, _, _) => {
          if (newUpload.files.nonEmpty) newUpload
          else
            throw new IllegalStateException(
              "Remote repository upload must specify some ids or files")
        }
      }

      onSuccess(
        UploadDao.insert(uploadToInsert, user).transact(xa).unsafeToFuture) {
        upload =>
          if (upload.uploadStatus == UploadStatus.Uploaded) {
            kickoffSceneImport(upload.id)
          }
          complete((StatusCodes.Created, upload))
      }
    }
  }

  def updateUpload(uploadId: UUID): Route = authenticate { user =>
    authorizeAsync {
      UploadDao.query
        .ownedByOrSuperUser(user, uploadId)
        .exists
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[Upload]) { updateUpload =>
        onSuccess {
          val x = for {
            u <- UploadDao.query.filter(uploadId).selectOption
            c <- UploadDao.update(updateUpload, uploadId, user)
          } yield {
            (u, c) match {
              case (Some(upload), 1) => {
                if (upload.uploadStatus != UploadStatus.Uploaded &&
                    updateUpload.uploadStatus == UploadStatus.Uploaded)
                  kickoffSceneImport(upload.id)
                StatusCodes.NoContent
              }
              case (_, 0) => StatusCodes.NotFound
              case (_, 1) => StatusCodes.NoContent
              case (_, _) => StatusCodes.NoContent
            }
          }
          x.transact(xa).unsafeToFuture
        } { s =>
          complete(s)
        }
      }
    }
  }

  def deleteUpload(uploadId: UUID): Route = authenticate { user =>
    authorizeAsync {
      UploadDao.query
        .ownedByOrSuperUser(user, uploadId)
        .exists
        .transact(xa)
        .unsafeToFuture
    } {
      onSuccess(
        UploadDao.query.filter(uploadId).delete.transact(xa).unsafeToFuture) {
        completeSingleOrNotFound
      }
    }
  }

  def getUploadCredentials(uploadId: UUID): Route = authenticate { user =>
    authorizeAsync {
      UploadDao.query.ownedBy(user, uploadId).exists.transact(xa).unsafeToFuture
    } {
      extractTokenHeader {
        case Some(jwt) =>
          onSuccess(
            UploadDao.query
              .filter(uploadId)
              .selectOption
              .transact(xa)
              .unsafeToFuture) {
            case Some(_) =>
              complete(
                CredentialsService.getCredentials(user,
                                                  uploadId,
                                                  jwt.split(" ").last))
            case None => complete(StatusCodes.NotFound)
          }
        case _ =>
          reject(
            AuthenticationFailedRejection(
              AuthenticationFailedRejection.CredentialsMissing,
              HttpChallenge("Bearer", "https://rasterfoundry.com")
            ))
      }
    }
  }
}
