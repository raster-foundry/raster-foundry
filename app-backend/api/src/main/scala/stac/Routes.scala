package com.rasterfoundry.api.stac

import com.rasterfoundry.akkautil._
import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.common.AWSBatch
import com.rasterfoundry.common.S3
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import cats.effect.IO
import com.amazonaws.services.s3.{AmazonS3URI}
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._

import java.net.URLDecoder
import java.util.UUID

trait StacRoutes
    extends Authentication
    with PaginationDirectives
    with UserErrorHandler
    with CommonHandlers
    with QueryParametersCommon
    with AWSBatch {
  val xa: Transactor[IO]

  val stacRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listStacExports } ~
        post { createStacExport }
    } ~
      pathPrefix(JavaUUID) { stacExportId =>
        pathEndOrSingleSlash {
          get { getStacExport(stacExportId) } ~
            delete { deleteStacExport(stacExportId) }
        }
      }
  }

  def signExportUrl(export: StacExport): StacExport.WithSignedDownload = {
    val s3Client = S3()
    export.exportLocation match {
      case Some(uri) => {
        val s3Uri =
          new AmazonS3URI(
            URLDecoder.decode(s"${uri}/catalog.zip", "utf-8")
          )
        s3Client.doesObjectExist(
          s3Uri.getBucket,
          s3Uri.getKey
        ) match {
          case true =>
            StacExport.signDownloadUrl(
              export,
              Some(
                s3Client
                  .getSignedUrl(s3Uri.getBucket, s3Uri.getKey)
                  .toString
              )
            )
          case _ =>
            StacExport.signDownloadUrl(
              export,
              None
            )
        }
      }
      case _ => StacExport.signDownloadUrl(export, None)
    }
  }

  def listStacExports: Route =
    authenticate {
      case (user, _) =>
        authorizeScope(
          ScopedAction(Domain.StacExports, Action.Read, None),
          user
        ) {
          (withPagination & stacExportQueryParameters) {
            (page: PageRequest, params: StacExportQueryParameters) =>
              complete {
                StacExportDao
                  .list(page, params, user)
                  .map(
                    p =>
                      PaginatedResponse[StacExport.WithSignedDownload](
                        p.count,
                        p.hasPrevious,
                        p.hasNext,
                        p.page,
                        p.pageSize,
                        p.results.map(signExportUrl(_))
                    ))
                  .transact(xa)
                  .unsafeToFuture
              }
          }
        }
    }

  def createStacExport: Route =
    authenticate {
      case (user, _) =>
        authorizeScope(
          ScopedAction(Domain.StacExports, Action.Create, None),
          user
        ) {
          entity(as[StacExport.Create]) { newStacExport =>
            ((newStacExport.toStacExport(user).includesCOG match {
              case true =>
                authorizeScope(
                  ScopedAction(Domain.StacExports, Action.CreateCOG, None),
                  user
                )
              case false => authorize(_ => true)
            }) & (authorizeAsync {
              newStacExport match {
                case StacExport
                      .AnnotationProjectExport(_, _, _, annotationProjectId) =>
                  AnnotationProjectDao
                    .authorized(
                      user,
                      ObjectType.AnnotationProject,
                      annotationProjectId,
                      ActionType.View
                    )
                    .map(_.toBoolean)
                    .transact(xa)
                    .unsafeToFuture
                case StacExport.CampaignExport(_, _, _, _, campaignId) =>
                  CampaignDao
                    .authorized(
                      user,
                      ObjectType.Campaign,
                      campaignId,
                      ActionType.View
                    )
                    .map(_.toBoolean)
                    .transact(xa)
                    .unsafeToFuture
              }
            })) {
              onSuccess(
                StacExportDao
                  .create(newStacExport, user)
                  .transact(xa)
                  .unsafeToFuture
              ) { stacExport =>
                kickoffStacExport(stacExport.id)
                complete((StatusCodes.Created, stacExport))
              }
            }
          }
        }
    }

  def getStacExport(id: UUID): Route =
    authenticate {
      case (user, _) =>
        authorizeScope(
          ScopedAction(Domain.StacExports, Action.Read, None),
          user
        ) {
          authorizeAsync {
            StacExportDao
              .isOwnerOrSuperUser(user, id)
              .transact(xa)
              .unsafeToFuture
          } {
            rejectEmptyResponse {
              complete {
                StacExportDao
                  .getById(id)
                  .map {
                    case Some(export) =>
                      Some(
                        signExportUrl(export)
                      )
                    case _ => None
                  }
                  .transact(xa)
                  .unsafeToFuture
              }
            }
          }
        }
    }

  def deleteStacExport(id: UUID): Route =
    authenticate {
      case (user, _) =>
        authorizeScope(
          ScopedAction(Domain.StacExports, Action.Delete, None),
          user
        ) {
          authorizeAsync {
            StacExportDao
              .isOwnerOrSuperUser(user, id)
              .transact(xa)
              .unsafeToFuture
          } {
            onSuccess(
              StacExportDao
                .delete(id)
                .transact(xa)
                .unsafeToFuture
            ) { count: Int =>
              complete((StatusCodes.NoContent, s"$count stac export deleted"))
            }
          }
        }
    }
}
