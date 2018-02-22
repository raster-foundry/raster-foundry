package com.azavea.rf.api.thumbnail

import com.azavea.rf.common.{UserErrorHandler, Authentication, S3, CommonHandlers}
import com.azavea.rf.database.ThumbnailDao
import com.azavea.rf.datamodel._
import com.azavea.rf.api.utils.Config

import io.circe._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.{StatusCodes, ContentType, HttpEntity, HttpResponse, MediaType, MediaTypes}
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import kamon.akka.http.KamonTraceDirectives

import java.util.UUID
import java.net.URI

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


trait ThumbnailRoutes extends Authentication
    with ThumbnailQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler
    with Config
    with KamonTraceDirectives {

  implicit def xa: Transactor[IO]

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
        ThumbnailDao.query.ownerFilter(user).filter(thumbnailParams).page(page).transact(xa).unsafeToFuture
      }
    }
  }

  def createThumbnail: Route = authenticate { user =>
    entity(as[Thumbnail.Create]) { newThumbnail =>
      authorize(user.isInRootOrSameOrganizationAs(newThumbnail)) {
        onSuccess(ThumbnailDao.insert(newThumbnail.toThumbnail, user).transact(xa).unsafeToFuture) { thumbnail =>
          complete(StatusCodes.Created, thumbnail)
        }
      }
    }
  }

  def getThumbnail(thumbnailId: UUID): Route = authenticate { user =>
    withPagination { page =>
      rejectEmptyResponse {
        complete {
          ThumbnailDao.query.ownerFilter(user).selectOption(thumbnailId).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def getThumbnailImage(thumbnailPath: String): Route = authenticateWithParameter { _ =>
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
        onSuccess(ThumbnailDao.update(updatedThumbnail, thumbnailId, user).transact(xa).unsafeToFuture) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteThumbnail(thumbnailId: UUID): Route = authenticate { user =>
    onSuccess(ThumbnailDao.query.ownerFilter(user).filter(fr"id = ${thumbnailId}").delete.transact(xa).unsafeToFuture) {
      completeSingleOrNotFound
    }
  }
}
