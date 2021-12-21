package com.rasterfoundry.api.thumbnail

import com.rasterfoundry.akkautil.PaginationDirectives
import com.rasterfoundry.akkautil.{
  Authentication,
  CommonHandlers,
  MembershipAndUser,
  UserErrorHandler
}
import com.rasterfoundry.api.utils.Config
import com.rasterfoundry.common.S3
import com.rasterfoundry.common.S3RegionEnum
import com.rasterfoundry.datamodel.{Action, Domain, ScopedAction}

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import cats.effect.IO
import com.amazonaws.regions._
import doobie.util.transactor.Transactor

import java.net.{URI, URLDecoder}

trait ThumbnailRoutes
    extends Authentication
    with ThumbnailQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler
    with Config {

  val xa: Transactor[IO]

  lazy val sentinelS3client = S3(
    region = Some(S3RegionEnum(Regions.EU_CENTRAL_1))
  )

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
    authenticateWithParameter {
      case (user, _) =>
        authorizeScope(
          ScopedAction(Domain.Thumbnails, Action.Read, None),
          user
        ) {
          val uriString =
            s"http://s3.amazonaws.com/${thumbnailBucket}/${thumbnailPath}"
          val uri = new URI(uriString)
          val s3Object = S3().getObject(uri)
          val metaData = S3.getObjectMetadata(s3Object)
          val s3MediaType = MediaType.parse(metaData.getContentType()) match {
            case Right(m) => m.asInstanceOf[MediaType.Binary]
            case Left(_)  => MediaTypes.`image/png`
          }
          complete(
            HttpResponse(
              entity = HttpEntity(
                ContentType(s3MediaType),
                S3.getObjectBytes(s3Object)
              )
            )
          )
        }
    }

  @SuppressWarnings(Array("AsInstanceOf"))
  def getProxiedThumbnailImage(thumbnailUri: String): Route =
    authenticateWithParameter {
      case (user, _) =>
        authorizeScope(
          ScopedAction(Domain.Scenes, Action.ReadThumbnail, None),
          user
        ) {
          val bucketAndPrefix =
            sentinelS3client.bucketAndPrefixFromURI(
              new URI(URLDecoder.decode(thumbnailUri, "UTF-8"))
            )
          val s3Object =
            sentinelS3client.getObject(
              bucketAndPrefix._1,
              bucketAndPrefix._2,
              true
            )
          val metaData = S3.getObjectMetadata(s3Object)
          val s3MediaType = MediaType.parse(metaData.getContentType()) match {
            case Right(m) => m.asInstanceOf[MediaType.Binary]
            case Left(_)  => MediaTypes.`image/png`
          }
          complete(
            HttpResponse(
              entity = HttpEntity(
                ContentType(s3MediaType),
                S3.getObjectBytes(s3Object)
              )
            )
          )
        }
    }
}
