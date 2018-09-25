package com.azavea.rf.api.thumbnail

import java.net.{URI, URLDecoder}

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import cats.effect.IO
import com.amazonaws.regions._
import com.amazonaws.services.s3.model.GetObjectRequest
import com.azavea.rf.api.utils.Config
import com.azavea.rf.authentication.Authentication
import com.azavea.rf.common.{CommonHandlers, S3, UserErrorHandler}
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import kamon.akka.http.KamonTraceDirectives

trait ThumbnailRoutes
    extends Authentication
    with ThumbnailQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler
    with Config
    with KamonTraceDirectives {

  val xa: Transactor[IO]

  lazy val sentinelS3client = S3.clientWithRegion(Regions.EU_CENTRAL_1)

  val thumbnailImageRoutes: Route = handleExceptions(userExceptionHandler) {
    pathPrefix("sentinel") {
      pathPrefix(Segment) { thumbnailUri =>
        pathEndOrSingleSlash {
          get { getProxiedThumbnailImage(thumbnailUri) }
        }
      }
    } ~
      pathPrefix(Segment) { thumbnailPath =>
        pathEndOrSingleSlash {
          get { getThumbnailImage(thumbnailPath) }
        }
      }
  }

  @SuppressWarnings(Array("AsInstanceOf"))
  def getThumbnailImage(thumbnailPath: String): Route =
    authenticateWithParameter { _ =>
      var uriString =
        s"http://s3.amazonaws.com/${thumbnailBucket}/${thumbnailPath}"
      val uri = new URI(uriString)
      val s3Object = S3.getObject(uri)
      val metaData = S3.getObjectMetadata(s3Object)
      val s3MediaType = MediaType.parse(metaData.getContentType()) match {
        case Right(m) => m.asInstanceOf[MediaType.Binary]
        case Left(_)  => MediaTypes.`image/png`
      }
      complete(
        HttpResponse(entity =
          HttpEntity(ContentType(s3MediaType), S3.getObjectBytes(s3Object))))
    }

  @SuppressWarnings(Array("AsInstanceOf"))
  def getProxiedThumbnailImage(thumbnailUri: String): Route =
    authenticateWithParameter { _ =>
      val bucketAndPrefix =
        S3.bucketAndPrefixFromURI(new URI(URLDecoder.decode(thumbnailUri)))
      val s3Object = sentinelS3client.getObject(
        new GetObjectRequest(bucketAndPrefix._1, bucketAndPrefix._2, true))
      val metaData = S3.getObjectMetadata(s3Object)
      val s3MediaType = MediaType.parse(metaData.getContentType()) match {
        case Right(m) => m.asInstanceOf[MediaType.Binary]
        case Left(_)  => MediaTypes.`image/png`
      }
      complete(
        HttpResponse(entity =
          HttpEntity(ContentType(s3MediaType), S3.getObjectBytes(s3Object))))
    }
}
