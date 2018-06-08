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

  val xa: Transactor[IO]

  val thumbnailImageRoutes: Route = handleExceptions(userExceptionHandler) {
    pathPrefix(Segment) { thumbnailPath =>
      pathEndOrSingleSlash {
        get { getThumbnailImage(thumbnailPath) }
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
}
