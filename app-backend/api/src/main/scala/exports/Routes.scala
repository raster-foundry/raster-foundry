package com.azavea.rf.api.exports

import akka.http.scaladsl.server.{PathMatcher, Route}
import akka.http.scaladsl.model.{StatusCodes, Uri}
import com.typesafe.scalalogging.LazyLogging
import cats.data._
import cats.implicits._
import com.lonelyplanet.akka.http.extensions.{PageRequest, PaginationDirectives}
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import io.circe._
import io.circe.syntax._
import com.azavea.rf.common._
import com.azavea.rf.datamodel._
import java.net.URL
import java.util.UUID

import cats.effect.IO
import com.azavea.rf.database.ExportDao

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success
import com.typesafe.scalalogging.LazyLogging
import doobie.util.transactor.Transactor
import com.azavea.rf.database.filter.Filterables._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._

trait ExportRoutes extends Authentication
  with ExportQueryParameterDirective
  with PaginationDirectives
  with CommonHandlers
  with UserErrorHandler
  with LazyLogging
  with AWSBatch {

  implicit def xa: Transactor[IO]

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

  def listExports: Route = authenticate { user =>
    (withPagination & exportQueryParams) {
      (page: PageRequest, queryParams: ExportQueryParameters) =>
        complete {
          ExportDao.query.filter(queryParams).ownerFilter(user).page(page).transact(xa).unsafeToFuture()
        }
    }
  }

  def getExport(exportId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        ExportDao.query.ownerFilter(user).filter(exportId).selectOption.transact(xa).unsafeToFuture()
      }
    }
  }

  def getExportDefinition(exportId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      val exportDefinition = for {
        export <- ExportDao.query.filter(exportId).select
        eo <- ExportDao.getExportDefinition(export, user)
      } yield eo
      onSuccess(exportDefinition.transact(xa).unsafeToFuture) { eo =>
        complete{eo}
      }
    }
  }

  def createExport: Route = authenticate { user =>
    entity(as[Export.Create]) { newExport =>
      authorize(user.isInRootOrSameOrganizationAs(newExport)) {
        newExport.exportOptions.as[ExportOptions] match {
          case Left(df:DecodingFailure) => complete((StatusCodes.BadRequest, s"JSON decoder exception: ${df.show}"))
          case Right(x) => {
            val updatedExport = user.updateDefaultExportSource(newExport.toExport(user))
            onSuccess(ExportDao.insert(updatedExport, user).transact(xa).unsafeToFuture) { export =>
              kickoffProjectExport(export.id)
              complete((StatusCodes.Created, export))
            }
          }
        }
      }
    }
  }

  def updateExport(exportId: UUID): Route = authenticate { user =>
    entity(as[Export]) { updateExport =>
      authorize(user.isInRootOrSameOrganizationAs(updateExport)) {
        onSuccess(ExportDao.update(updateExport, exportId, user).transact(xa).unsafeToFuture) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteExport(exportId: UUID): Route = authenticate { user =>
    onSuccess(ExportDao.query.filter(exportId).delete.transact(xa).unsafeToFuture) {
      completeSingleOrNotFound
    }
  }

  def exportFiles(exportId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        (for {
          export: Export <- OptionT(ExportDao.query.filter(exportId).selectOption.transact(xa).unsafeToFuture)
          list: List[String] <- OptionT.fromOption[Future] { export.getExportOptions.map(_.getSignedUrls(): List[String]) }
        } yield list).value
      }
    }
  }

  def proxiedFiles(exportId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        (for {
          export: Export <- OptionT(ExportDao.query.filter(exportId).selectOption.transact(xa).unsafeToFuture)
          list: List[String] <- OptionT.fromOption[Future] { export.getExportOptions.map(_.getObjectKeys(): List[String]) }
        } yield list).value
      }
    }
  }

  def redirectRoute(exportId: UUID, objectKey: String): Route = authenticateWithParameter { user =>
    implicit def javaURLAsAkkaURI(url: URL): Uri = Uri(url.toString)
    val x: Future[Option[Uri]] =
      OptionT(ExportDao.query.filter(exportId).selectOption.transact(xa).unsafeToFuture)
        .flatMap { y: Export => { OptionT.fromOption[Future]{y.getExportOptions.map(_.getSignedUrl(objectKey): Uri)}}}.value

    onComplete(x) { y =>
      y match {
        case Success(Some(z)) => redirect(z, StatusCodes.TemporaryRedirect)
        case _ => throw new Exception
      }
    }
  }
}
