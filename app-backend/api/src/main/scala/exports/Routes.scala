package com.rasterfoundry.api.exports

import com.rasterfoundry.akkautil._
import com.rasterfoundry.common._
import com.rasterfoundry.database.ExportDao
import com.rasterfoundry.database.filter.Filterables._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Route
import cats.data._
import cats.effect.IO
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.circe._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Success

import java.net.URL
import java.util.UUID

trait ExportRoutes
    extends Authentication
    with ExportQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler
    with LazyLogging
    with AWSBatch {

  val xa: Transactor[IO]

  implicit val ec: ExecutionContext

  val exportRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listExports } ~
        post { createExport }
    } ~
      pathPrefix(JavaUUID) { exportId =>
        pathEndOrSingleSlash {
          get { getExport(exportId) } ~
            put { updateExport(exportId) } ~
            delete { deleteExport(exportId) }
        } ~
          pathPrefix("definition") {
            pathEndOrSingleSlash {
              get { getExportDefinition(exportId) }
            }
          } ~
          pathPrefix("files") {
            pathEndOrSingleSlash {
              get { proxiedFiles(exportId) }
            } ~
              pathPrefix(Segment) { objectKey =>
                pathEndOrSingleSlash {
                  redirectRoute(exportId, objectKey)
                }
              }
          }
      }
  }

  def listExports: Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Exports, Action.Read, None), user) {
      (withPagination & exportQueryParams) {
        (page: PageRequest, queryParams: ExportQueryParameters) =>
          complete {
            ExportDao.query
              .filter(queryParams)
              .filter(user)
              .page(page)
              .transact(xa)
              .unsafeToFuture()
          }
      }
    }
  }

  def getExport(exportId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Exports, Action.Read, None), user) {
      authorizeAsync {
        ExportDao.query
          .ownedByOrSuperUser(user, exportId)
          .exists
          .transact(xa)
          .unsafeToFuture
      } {
        rejectEmptyResponse {
          complete {
            ExportDao.query
              .filter(exportId)
              .selectOption
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }
  }

  def getExportDefinition(exportId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Exports, Action.Read, None), user) {
      authorizeAsync {
        ExportDao.query
          .ownedByOrSuperUser(user, exportId)
          .exists
          .transact(xa)
          .unsafeToFuture
      } {
        rejectEmptyResponse {
          val exportDefinition = for {
            export <- ExportDao.query.filter(exportId).select
            eo <- ExportDao.getExportDefinition(export)
          } yield eo
          onSuccess(exportDefinition.transact(xa).unsafeToFuture) { eo =>
            complete {
              eo
            }
          }
        }
      }
    }
  }

  def createExport: Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Exports, Action.Create, None), user) {
      entity(as[Export.Create]) { newExport =>
        newExport.exportOptions.as[ExportOptions] match {
          case Left(df: DecodingFailure) =>
            complete(
              (StatusCodes.BadRequest, s"JSON decoder exception: ${df.show}")
            )
          case Right(_) => {
            val updatedExport =
              user.updateDefaultExportSource(newExport.toExport(user))
            onSuccess(
              ExportDao.insert(updatedExport, user).transact(xa).unsafeToFuture
            ) { export =>
              kickoffProjectExport(export.id)
              complete((StatusCodes.Created, export))
            }
          }
        }
      }
    }
  }

  def updateExport(exportId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Exports, Action.Update, None), user) {
      authorizeAsync {
        ExportDao.query
          .ownedBy(user, exportId)
          .exists
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[Export]) { updateExport =>
          onSuccess(
            ExportDao
              .update(updateExport, exportId)
              .transact(xa)
              .unsafeToFuture
          ) {
            completeSingleOrNotFound
          }
        }
      }
    }
  }

  def deleteExport(exportId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Exports, Action.Delete, None), user) {
      authorizeAsync {
        ExportDao.query
          .ownedBy(user, exportId)
          .exists
          .transact(xa)
          .unsafeToFuture
      } {
        onSuccess(
          ExportDao.query.filter(exportId).delete.transact(xa).unsafeToFuture
        ) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def exportFiles(exportId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Exports, Action.Read, None), user) {
      authorizeAsync {
        ExportDao.query
          .ownedBy(user, exportId)
          .exists
          .transact(xa)
          .unsafeToFuture
      } {
        rejectEmptyResponse {
          complete {
            (for {
              export: Export <- OptionT(
                ExportDao.query
                  .filter(exportId)
                  .selectOption
                  .transact(xa)
                  .unsafeToFuture
              )
              list: List[String] <- OptionT.fromOption[Future] {
                export.getExportOptions.map(_.getSignedUrls(): List[String])
              }
            } yield list).value
          }
        }
      }
    }
  }

  def proxiedFiles(exportId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Exports, Action.Read, None), user) {
      authorizeAsync {
        ExportDao.query
          .ownedBy(user, exportId)
          .exists
          .transact(xa)
          .unsafeToFuture
      } {
        rejectEmptyResponse {
          complete {
            (for {
              export: Export <- OptionT(
                ExportDao.query
                  .filter(exportId)
                  .selectOption
                  .transact(xa)
                  .unsafeToFuture
              )
              list: List[String] <- OptionT.fromOption[Future] {
                export.getExportOptions.map(_.getObjectKeys(): List[String])
              }
            } yield list).value
          }
        }
      }
    }
  }

  def redirectRoute(exportId: UUID, objectKey: String): Route =
    authenticateWithParameter { user =>
      authorizeScope(ScopedAction(Domain.Exports, Action.Read, None), user) {
        authorizeAsync {
          ExportDao.query
            .ownedBy(user, exportId)
            .exists
            .transact(xa)
            .unsafeToFuture
        } {
          implicit def javaURLAsAkkaURI(url: URL): Uri = Uri(url.toString)

          val x: Future[Option[Uri]] =
            OptionT(
              ExportDao.query
                .filter(exportId)
                .selectOption
                .transact(xa)
                .unsafeToFuture
            ).flatMap { y: Export =>
              {
                OptionT.fromOption[Future] {
                  y.getExportOptions.map(_.getSignedUrl(objectKey): Uri)
                }
              }
            }.value

          onComplete(x) {
            case Success(Some(z)) => redirect(z, StatusCodes.TemporaryRedirect)
            case _                => throw new Exception
          }
        }
      }
    }
}
