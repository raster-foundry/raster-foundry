package com.azavea.rf.api.uploads

import java.util.UUID

import scala.util.{Success, Failure}

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes

import com.lonelyplanet.akka.http.extensions.{PaginationDirectives, PageRequest}

import com.azavea.rf.common.{Authentication, UserErrorHandler}
import com.azavea.rf.database.tables.Uploads
import com.azavea.rf.database.query._
import com.azavea.rf.database.{Database, ActionRunner}
import com.azavea.rf.datamodel._
import io.circe._
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.CirceSupport._

trait UploadRoutes extends Authentication
    with UploadQueryParameterDirective
    with PaginationDirectives
    with UserErrorHandler
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
        list[Upload](Uploads.listUploads(page.offset, page.limit, queryParams),
          page.offset, page.limit)
      }
    }
  }

  def getUpload(uploadId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        readOne[Upload](Uploads.getUpload(uploadId))
      }
    }
  }

  def createUpload: Route = authenticate { user =>
    entity(as[Upload.Create]) { newUpload =>
      onSuccess(write[Upload](Uploads.insertUpload(newUpload, user))) { upload =>
        complete(upload)
      }
    }
  }

  def updateUpload(uploadId: UUID): Route = authenticate { user =>
    entity(as[Upload]) { updateUpload =>
      onSuccess(update(Uploads.updateUpload(updateUpload, uploadId, user))) { count =>
        complete(StatusCodes.NoContent)
      }
    }
  }

  def deleteUpload(uploadId: UUID): Route = authenticate { user =>
    onSuccess(drop(Uploads.deleteUpload(uploadId))) {
      case 1 => complete(StatusCodes.NoContent)
      case 0 => complete(StatusCodes.NotFound)
      case count => throw new IllegalStateException(
        s"Error deleting upload. Delete result expected to be 1, was $count"
      )
    }
  }

  def getUploadCredentials(uploadId: UUID): Route = authenticate { user =>
    validateTokenHeader { jwt =>
      onSuccess(readOne[Upload](Uploads.getUpload(uploadId))) {
        case Some(_) => complete(Auth0DelegationService.getCredentials(user, uploadId, jwt))
        case None => complete(StatusCodes.NotFound)
      }
    }
  }
}
