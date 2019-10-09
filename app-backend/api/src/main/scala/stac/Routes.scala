package com.rasterfoundry.api.stac

import com.rasterfoundry.akkautil._
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._
import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.common.AWSBatch
import com.rasterfoundry.common.S3

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import cats.effect.IO
import com.amazonaws.services.s3.{AmazonS3URI}

import java.util.UUID
import java.net.URLDecoder

import doobie._
import doobie.implicits._

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

  def listStacExports: Route = authenticate { user =>
    (withPagination & stacExportQueryParameters) {
      (page: PageRequest, params: StacExportQueryParameters) =>
        complete {
          val s3Client = S3()
          StacExportDao
            .list(page, params, user)
            .map(p =>
              PaginatedResponse[StacExport.WithSignedDownload](
                p.count,
                p.hasPrevious,
                p.hasNext,
                p.page,
                p.pageSize,
                p.results.map(export =>
                  StacExport.signDownloadUrl(
                    export,
                    export.exportLocation.map(uri => {

                      val s3Uri =
                        new AmazonS3URI(URLDecoder.decode(uri, "utf-8"))
                      s3Client
                        .getSignedUrl(s3Uri.getBucket,
                                      s"${s3Uri.getKey}/catalog.zip")
                        .toString
                    })
                ))
            ))
            .transact(xa)
            .unsafeToFuture
        }
    }
  }

  def createStacExport: Route = authenticate { user =>
    entity(as[StacExport.Create]) { newStacExport =>
      authorizeAsync {
        StacExportDao
          .hasProjectViewAccess(newStacExport.layerDefinitions, user)
          .transact(xa)
          .unsafeToFuture
      } {
        onSuccess(
          StacExportDao
            .create(newStacExport, user)
            .transact(xa)
            .unsafeToFuture) { stacExport =>
          kickoffStacExport(stacExport.id)
          complete((StatusCodes.Created, stacExport))
        }
      }
    }
  }

  def getStacExport(id: UUID): Route = authenticate { user =>
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
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def deleteStacExport(id: UUID): Route = authenticate { user =>
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
          .unsafeToFuture) { count: Int =>
        complete((StatusCodes.NoContent, s"$count stac export deleted"))
      }
    }
  }
}
