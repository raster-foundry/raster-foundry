package com.rasterfoundry.api.uploads

import com.rasterfoundry.akkautil._
import com.rasterfoundry.api.utils.Config
import com.rasterfoundry.common.{AWSBatch, S3}
import com.rasterfoundry.database.UploadDao
import com.rasterfoundry.database.filter.Filterables._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.HttpChallenge
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Route}
import cats.effect.IO
import com.amazonaws.HttpMethod
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie.implicits._
import doobie.util.transactor.Transactor
import geotrellis.spark.io.s3.S3Client

import java.util.UUID

trait UploadRoutes
    extends Authentication
    with UploadQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler
    with AWSBatch
    with Config {
  val xa: Transactor[IO]

  val s3 = S3()

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
          } ~ pathPrefix("signed-url") {
          get { getSignedUploadUrl(uploadId) }
        }
      }
  }

  def listUploads: Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Uploads, Action.Read, None), user) {
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
  }

  def getUpload(uploadId: UUID): Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Uploads, Action.Read, None), user) {
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
  }

  def createUpload: Route = authenticate { user =>
    val userBytesUploaded =
      UploadDao.getUserBytesUploaded(user).transact(xa).unsafeToFuture()
    authorizeScopeLimit(
      userBytesUploaded,
      ScopedAction(Domain.Uploads, Action.Create, None),
      user
    ) {
      entity(as[Upload.Create]) { newUpload =>
        logger.debug(
          s"newUpload: ${newUpload.uploadType}, ${newUpload.source}, ${newUpload.fileType}"
        )
        val uploadToInsert =
          (newUpload.uploadType, newUpload.source, newUpload.fileType) match {
            case (UploadType.S3, _, FileType.NonSpatial) => {
              if (newUpload.files.nonEmpty) newUpload
              else
                throw new IllegalStateException(
                  "S3 upload must specify a source if no files are specified"
                )
            }
            case (UploadType.S3, Some(source), _) => {
              if (newUpload.files.nonEmpty) newUpload
              else {
                val files = listAllowedFilesInS3Source(source)
                if (files.nonEmpty) newUpload.copy(files = files)
                else
                  throw new IllegalStateException(
                    "No acceptable files found in the provided source"
                  )
              }
            }
            case (UploadType.S3, None, _) => {
              if (newUpload.files.nonEmpty) newUpload
              else
                throw new IllegalStateException(
                  "S3 upload must specify a source if no files are specified"
                )
            }
            case (_, _, FileType.GeoJson) => {
              throw new IllegalStateException(
                "Scene uploads must contain imagery, not GeoJson"
              )
            }
            case (_, _, _) => {
              if (newUpload.files.nonEmpty) newUpload
              else
                throw new IllegalStateException(
                  "Remote repository upload must specify some ids or files"
                )
            }
          }

        val bytesUploaded = Upload.getBytesUploaded(
          S3Client.DEFAULT,
          dataBucket,
          newUpload.files,
          newUpload.uploadStatus,
          newUpload.uploadType
        )

        println(s"BYTES UPLOADED: $bytesUploaded")

        onSuccess(
          UploadDao
            .insert(uploadToInsert, user, bytesUploaded)
            .transact(xa)
            .unsafeToFuture
        ) { upload =>
          if (upload.uploadStatus == UploadStatus.Uploaded) {
            kickoffSceneImport(upload.id)
          }
          complete((StatusCodes.Created, upload))
        }
      }
    }
  }

  def updateUpload(uploadId: UUID): Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Uploads, Action.Update, None), user) {
      authorizeAsync {
        UploadDao.query
          .ownedByOrSuperUser(user, uploadId)
          .exists
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[Upload]) { updateUpload =>
          val updatedBytesUploaded = Upload.getBytesUploaded(
            S3Client.DEFAULT,
            dataBucket,
            updateUpload.files,
            updateUpload.uploadStatus,
            updateUpload.uploadType
          )
          onSuccess {
            val x = for {
              u <- UploadDao.query.filter(uploadId).selectOption
              c <- UploadDao.update(
                updateUpload.copy(bytesUploaded = updatedBytesUploaded),
                uploadId
              )
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
  }

  def deleteUpload(uploadId: UUID): Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Uploads, Action.Delete, None), user) {
      authorizeAsync {
        UploadDao.query
          .ownedByOrSuperUser(user, uploadId)
          .exists
          .transact(xa)
          .unsafeToFuture
      } {
        onSuccess(
          UploadDao.query.filter(uploadId).delete.transact(xa).unsafeToFuture
        ) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def getUploadCredentials(uploadId: UUID): Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Uploads, Action.Create, None), user) {
      authorizeAsync {
        UploadDao.query
          .ownedBy(user, uploadId)
          .exists
          .transact(xa)
          .unsafeToFuture
      } {
        extractTokenHeader {
          case Some(jwt) =>
            onSuccess(
              UploadDao.query
                .filter(uploadId)
                .selectOption
                .transact(xa)
                .unsafeToFuture
            ) {
              case Some(_) =>
                complete(
                  CredentialsService
                    .getCredentials(user, uploadId, jwt.split(" ").last)
                )
              case None => complete(StatusCodes.NotFound)
            }
          case _ =>
            reject(
              AuthenticationFailedRejection(
                AuthenticationFailedRejection.CredentialsMissing,
                HttpChallenge("Bearer", "https://rasterfoundry.com")
              )
            )
        }
      }
    }
  }

  def getSignedUploadUrl(uploadId: UUID): Route = authenticate { user =>
    authorizeScope(ScopedAction(Domain.Uploads, Action.Read, None), user) {
      authorizeAsync {
        UploadDao.query
          .ownedBy(user, uploadId)
          .exists
          .transact(xa)
          .unsafeToFuture
      } {
        complete {
          val signed = s3.getSignedUrl(
            dataBucket,
            s"user-uploads/${user.id}/${uploadId}/${uploadId}.tif",
            method = HttpMethod.PUT
          )
          Upload.PutUrl(s"$signed")
        }
      }
    }
  }
}
