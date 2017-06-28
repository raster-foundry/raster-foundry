package com.azavea.rf.api.thumbnail

import com.azavea.rf.common.{UserErrorHandler, Authentication, S3, CommonHandlers}
import com.azavea.rf.database.tables.Thumbnails
import com.azavea.rf.database.Database
import com.azavea.rf.datamodel._
import com.azavea.rf.api.utils.Config

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.{StatusCodes, ContentType, HttpEntity, HttpResponse, MediaType, MediaTypes}
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import kamon.akka.http.KamonTraceDirectives

import java.util.UUID
import java.net.URI

trait ThumbnailRoutes extends Authentication
    with ThumbnailQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler
    with Config
    with KamonTraceDirectives {

  implicit def database: Database

  val thumbnailRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listThumbnails } ~
      post {
        traceName("thumbnails-list") {
          createThumbnail
        }
      }
    } ~
    pathPrefix(JavaUUID) { thumbnailId =>
      pathEndOrSingleSlash {
        get { traceName("thumbnails-detail") {
          getThumbnail(thumbnailId) }
        } ~
        put { updateThumbnail(thumbnailId) } ~
        delete { deleteThumbnail(thumbnailId) }
      }
    } ~
    pathPrefix(Segment) { thumbnailPath =>
      pathEndOrSingleSlash {
        get { getThumbnailImage(thumbnailPath) }
      }
    }
  }

  val thumbnailImageRoutes: Route = handleExceptions(userExceptionHandler) {
    pathPrefix(Segment) { thumbnailPath =>
      pathEndOrSingleSlash {
        get { getThumbnailImage(thumbnailPath) }
      }
    }
  }

  def listThumbnails: Route = authenticate { user =>
    (withPagination & thumbnailSpecificQueryParameters) { (page, thumbnailParams) =>
      complete {
        Thumbnails.listThumbnails(page, thumbnailParams, user)
      }
    }
  }

  def createThumbnail: Route = authenticate { user =>
    entity(as[Thumbnail.Create]) { newThumbnail =>
      authorize(user.isInRootOrSameOrganizationAs(newThumbnail)) {
        onSuccess(Thumbnails.insertThumbnail(newThumbnail.toThumbnail)) { thumbnail =>
          complete(StatusCodes.Created, thumbnail)
        }
      }
    }
  }

  def getThumbnail(thumbnailId: UUID): Route = authenticate { user =>
    withPagination { page =>
      rejectEmptyResponse {
        complete {
          Thumbnails.getThumbnail(thumbnailId, user)
        }
      }
    }
  }

  def getThumbnailImage(thumbnailPath: String): Route = validateTokenParameter { token =>
    var uriString = s"http://s3.amazonaws.com/${thumbnailBucket}/${thumbnailPath}"
    val uri = new URI(uriString)
    val s3Object = S3.getObject(uri)
    val metaData = S3.getObjectMetadata(s3Object)
    val s3MediaType = MediaType.parse(metaData.getContentType()) match {
      case Right(m) => m.asInstanceOf[MediaType.Binary]
      case Left(_) => MediaTypes.`image/png`
    }
    complete(HttpResponse(entity =
      HttpEntity(ContentType(s3MediaType), S3.getObjectBytes(s3Object))
    ))
  }

  def updateThumbnail(thumbnailId: UUID): Route = authenticate { user =>
    entity(as[Thumbnail]) { updatedThumbnail =>
      authorize(user.isInRootOrSameOrganizationAs(updatedThumbnail)) {
        onSuccess(Thumbnails.updateThumbnail(updatedThumbnail, thumbnailId, user)) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteThumbnail(thumbnailId: UUID): Route = authenticate { user =>
    onSuccess(Thumbnails.deleteThumbnail(thumbnailId, user)) {
      completeSingleOrNotFound
    }
  }
}
