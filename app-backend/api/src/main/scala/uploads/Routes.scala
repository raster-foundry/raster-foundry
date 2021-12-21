package com.rasterfoundry.api.uploads

import com.rasterfoundry.akkautil._
import com.rasterfoundry.api.utils.Config
import com.rasterfoundry.common.{AWSBatch, S3}
import com.rasterfoundry.database.UploadDao
import com.rasterfoundry.database.filter.Filterables._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.model.headers.HttpChallenge
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Route}
import cats.effect.IO
import cats.implicits._
import com.amazonaws.HttpMethod
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor

import scala.concurrent.Future
import scala.util.Success

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

  def listUploads: Route =
    authenticate {
      case (user, _) =>
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

  def getUpload(uploadId: UUID): Route =
    authenticate {
      case (user, _) =>
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

  def createUpload: Route =
    authenticate {
      case (user, _) =>
        // check if user has already hit the storage quota
        val userBytesUploaded =
          UploadDao.getUserBytesUploaded(user).transact(xa).unsafeToFuture()
        authorizeScopeLimit(
          userBytesUploaded,
          Domain.Uploads,
          Action.Create,
          user
        ) {
          entity(as[Upload.Create]) { newUpload =>
            logger.debug(
              s"newUpload: ${newUpload.uploadType}, ${newUpload.source}, ${newUpload.fileType}"
            )
            // check the bytes uploaded to this S3 location
            val bytesUploaded = Upload.getBytesUploaded(
              s3.client,
              dataBucket,
              newUpload.files,
              newUpload.uploadStatus,
              newUpload.uploadType
            )
            // check the file size in bytes submitted by this request
            // the potential size should be the max of
            // the fileSize and the S3 file size check
            val potentialNewBytes =
              newUpload.fileSizeBytes.getOrElse(0.toLong).max(bytesUploaded)
            val uploadToInsert =
              (
                newUpload.uploadType,
                newUpload.source,
                newUpload.fileType
              ) match {
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
            authorizeScopeLimit(
              Future.successful(potentialNewBytes),
              Domain.Uploads,
              Action.Create,
              user
            ) {
              onComplete {
                for {
                  upload <- UploadDao
                    .insert(uploadToInsert, user, potentialNewBytes)
                    .transact(xa)
                    .unsafeToFuture
                } yield upload
              } {
                case Success(upload) =>
                  if (upload.uploadStatus == UploadStatus.Uploaded) {
                    kickoffSceneImport(upload.id)
                  }
                  complete((StatusCodes.Created, upload))
                case _ =>
                  complete { HttpResponse(StatusCodes.BadRequest) }
              }
            }

          }
        }
    }

  def updateUpload(uploadId: UUID): Route =
    authenticate {
      case (user, _) =>
        authorizeScope(ScopedAction(Domain.Uploads, Action.Update, None), user) {
          authorizeAsync {
            UploadDao.query
              .ownedByOrSuperUser(user, uploadId)
              .exists
              .transact(xa)
              .unsafeToFuture
          } {
            entity(as[Upload]) { updateUpload =>
              // bytes of the files listed in the upload object to update
              val updatedBytesUploaded = Upload.getBytesUploaded(
                s3.client,
                dataBucket,
                updateUpload.files,
                updateUpload.uploadStatus,
                updateUpload.uploadType
              )
              onComplete {
                (for {
                  uploadOpt <- UploadDao.getUploadById(uploadId)
                  usedBytes <- uploadOpt traverse { upload =>
                    // get the bytes the user already used
                    // excluding this upload to be updated
                    UploadDao.getUserBytesUploaded(user, Some(upload.id))
                  }
                  updated <- usedBytes traverse { used =>
                    // check if the existing bytes and the bytes to be updated
                    // exceed the quota
                    val isBelow = isBelowLimitCheck(
                      used + updatedBytesUploaded,
                      Domain.Uploads,
                      Action.Create,
                      user
                    )
                    if (isBelow == true) {
                      UploadDao.update(
                        updateUpload.copy(bytesUploaded = updatedBytesUploaded),
                        uploadId
                      )
                    } else {
                      -1.pure[ConnectionIO]
                    }
                  }
                } yield (uploadOpt, updated)).transact(xa).unsafeToFuture
              } {
                case Success((Some(upload), Some(rowsUpdated))) => {
                  if (rowsUpdated == -1) {
                    return complete { HttpResponse(StatusCodes.Forbidden) }
                  }
                  if (rowsUpdated == 0) {
                    return complete { HttpResponse(StatusCodes.NoContent) }
                  }
                  if (upload.uploadStatus != UploadStatus.Uploaded &&
                      updateUpload.uploadStatus == UploadStatus.Uploaded) {
                    kickoffSceneImport(upload.id)
                  }
                  complete { HttpResponse(StatusCodes.NoContent) }
                }
                case Success((None, _)) =>
                  complete { HttpResponse(StatusCodes.NotFound) }
                case _ => complete { HttpResponse(StatusCodes.BadRequest) }
              }
            }
          }
        }
    }

  def deleteUpload(uploadId: UUID): Route =
    authenticate {
      case (user, _) =>
        authorizeScope(ScopedAction(Domain.Uploads, Action.Delete, None), user) {
          authorizeAsync {
            UploadDao.query
              .ownedByOrSuperUser(user, uploadId)
              .exists
              .transact(xa)
              .unsafeToFuture
          } {
            onSuccess(
              UploadDao.query
                .filter(uploadId)
                .delete
                .transact(xa)
                .unsafeToFuture
            ) {
              completeSingleOrNotFound
            }
          }
        }
    }

  def getUploadCredentials(uploadId: UUID): Route =
    authenticate {
      case (user, _) =>
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
                  case Some(upload) =>
                    complete(
                      CredentialsService
                        .getCredentials(upload, jwt.split(" ").last)
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

  def getSignedUploadUrl(uploadId: UUID): Route =
    authenticate {
      case (user, _) =>
        authorizeScope(ScopedAction(Domain.Uploads, Action.Read, None), user) {
          authorizeAsync {
            UploadDao.query
              .ownedBy(user, uploadId)
              .exists
              .transact(xa)
              .unsafeToFuture
          } {
            onSuccess(
              UploadDao.query
                .filter(uploadId)
                .selectOption
                .transact(xa)
                .unsafeToFuture
            ) {
              case Some(upload) =>
                complete {
                  // NOTE: We expect there to be exactly one file for this upload
                  upload.files.headOption match {
                    case Some(filename) => {
                      val signed = s3.getSignedUrl(
                        dataBucket,
                        s"${upload.s3Path}/$filename",
                        method = HttpMethod.PUT
                      )
                      Upload.PutUrl(s"$signed")
                    }
                    case None => StatusCodes.BadRequest
                  }
                }
              case None => complete(StatusCodes.NotFound)
            }
          }
        }
    }
}
