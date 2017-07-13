package com.azavea.rf.api.uploads

import java.net.URI
import java.util.UUID

import scala.concurrent.ExecutionContext.Implicits.global
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import com.lonelyplanet.akka.http.extensions.{PageRequest, PaginationDirectives}
import com.azavea.rf.common.{Airflow, Authentication, CommonHandlers, UserErrorHandler, S3}
import com.azavea.rf.database.tables.Uploads
import com.azavea.rf.database.query._
import com.azavea.rf.database.{ActionRunner, Database}
import com.azavea.rf.datamodel._
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.CirceSupport._


trait UploadRoutes extends Authentication
    with UploadQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler
    with Airflow
    with ActionRunner {
  implicit def database: Database

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
        list[Upload](Uploads.listUploads(page.offset, page.limit, queryParams, user),
          page.offset, page.limit)
      }
    }
  }

  def getUpload(uploadId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        readOne[Upload](Uploads.getUpload(uploadId, user))
      }
    }
  }

  def createUpload: Route = authenticate { user =>
    entity(as[Upload.Create]) { newUpload =>
      authorize(user.isInRootOrSameOrganizationAs(newUpload)) {
        val decodedMetadata = decode[UploadMetadata](newUpload.metadata.asJson.noSpaces) match {
          case Left(_) =>
            throw new IllegalStateException(s"Could not parse  ${newUpload.metadata} to uploadMetadata")
          case Right(data) => data
        }
        val withParsedMetadata = newUpload.copy(sceneMetadata = decodedMetadata)
        val uploadToInsert = (withParsedMetadata.uploadType, withParsedMetadata.source) match {
          case (UploadType.S3, Some(source)) => {
            if (withParsedMetadata.files.nonEmpty) withParsedMetadata
            else {
              val files = listAllowedFilesInS3Source(source)
              if (files.nonEmpty) withParsedMetadata.copy(files = files)
              else throw new IllegalStateException("No acceptable files found in the provided source")
            }
          }
          case (UploadType.S3, None) => {
            if (withParsedMetadata.files.nonEmpty) withParsedMetadata
            else throw new IllegalStateException("S3 upload must specify a source if no files are specified")
          }
          case (UploadType.Local, _) => withParsedMetadata
          case _ => throw new IllegalStateException("Unsupported import type")
        }

        onSuccess(write[Upload](Uploads.insertUpload(uploadToInsert, user))) { upload =>
          if (upload.uploadStatus == UploadStatus.Uploaded) {
            kickoffSceneImport(upload.id)
          }
          complete(upload)
        }
      }
    }
  }

  def updateUpload(uploadId: UUID): Route = authenticate { user =>
    entity(as[Upload]) { updateUpload =>
      authorize(user.isInRootOrSameOrganizationAs(updateUpload)) {
        onSuccess(for {
          u <- readOne[Upload](Uploads.getUpload(updateUpload.id, user))
          c <- update(Uploads.updateUpload(updateUpload, uploadId, user))
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
        }){ s => complete(s) }
      }
    }
  }

  def deleteUpload(uploadId: UUID): Route = authenticate { user =>
    onSuccess(drop(Uploads.deleteUpload(uploadId, user))) {
      completeSingleOrNotFound
    }
  }

  def getUploadCredentials(uploadId: UUID): Route = authenticate { user =>
    validateTokenHeader { jwt =>
      onSuccess(readOne[Upload](Uploads.getUpload(uploadId, user))) {
        case Some(_) => complete(Auth0DelegationService.getCredentials(user, uploadId, jwt))
        case None => complete(StatusCodes.NotFound)
      }
    }
  }
}
