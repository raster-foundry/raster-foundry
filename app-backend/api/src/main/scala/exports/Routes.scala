package com.azavea.rf.api.exports

import akka.http.scaladsl.server.{Route, PathMatcher}
import akka.http.scaladsl.model.{StatusCodes, Uri}
import com.typesafe.scalalogging.LazyLogging
import cats.data._
import cats.implicits._
import com.lonelyplanet.akka.http.extensions.{PageRequest, PaginationDirectives}
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe._
import io.circe.syntax._

import com.azavea.rf.common._
import com.azavea.rf.database.tables.{Exports, Users}
import com.azavea.rf.database.query._
import com.azavea.rf.database.{ActionRunner, Database}
import com.azavea.rf.datamodel._

import java.net.URL
import java.util.UUID

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success

trait ExportRoutes extends Authentication
  with ExportQueryParameterDirective
  with PaginationDirectives
  with CommonHandlers
  with UserErrorHandler
  with LazyLogging
  with Airflow
  with ActionRunner {
  implicit def database: Database

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
          list[Export](Exports.listExports(page.offset, page.limit, queryParams, user),
            page.offset, page.limit)
        }
    }
  }

  def getExport(exportId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        readOne[Export](Exports.getExport(exportId, user))
      }
    }
  }

  def getExportDefinition(exportId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        readOne[Export](Exports.getExport(exportId, user))
          .map { _.map { Exports.getExportDefinition(_, user) } }
          .map(_.sequence.map(_.flatten)).flatten
      }
    }
  }

  def createExport: Route = authenticate { user =>
    entity(as[Export.Create]) { newExport =>
      authorize(user.isInRootOrSameOrganizationAs(newExport)) {
        newExport.exportOptions.as[ExportOptions] match {
          case Left(df:DecodingFailure) => complete((StatusCodes.BadRequest, s"JSON decoder exception: ${df.show}"))
          case Right(x) =>
            onSuccess(write(Exports.insertExport(newExport, user))) { export =>
              val updateExport = user.updateDefaultExportSource(export)
              kickoffProjectExport(updateExport.id)
              update(Exports.updateExport(updateExport, updateExport.id, user))
              complete(updateExport)
            }
        }
      }
    }
  }

  def updateExport(exportId: UUID): Route = authenticate { user =>
    entity(as[Export]) { updateExport =>
      authorize(user.isInRootOrSameOrganizationAs(updateExport)) {
        onSuccess(update(Exports.updateExport(updateExport, exportId, user))) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteExport(exportId: UUID): Route = authenticate { user =>
    onSuccess(drop(Exports.deleteExport(exportId, user))) {
      completeSingleOrNotFound
    }
  }

  def exportFiles(exportId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        (for {
          export: Export <- OptionT(readOne[Export](Exports.getExportWithStatus(exportId, user, ExportStatus.Exported)))
          list: List[String] <- OptionT.fromOption[Future] { export.getExportOptions.map(_.getSignedUrls(): List[String]) }
        } yield list).value
      }
    }
  }

  def proxiedFiles(exportId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        (for {
          export: Export <- OptionT(readOne[Export](Exports.getExportWithStatus(exportId, user, ExportStatus.Exported)))
          list: List[String] <- OptionT.fromOption[Future] { export.getExportOptions.map(_.getObjectKeys(): List[String]) }
        } yield list).value
      }
    }
  }

  def redirectRoute(exportId: UUID, objectKey: String): Route = authenticateWithParameter { user =>
    implicit def javaURLAsAkkaURI(url: URL): Uri = Uri(url.toString)
    val x: Future[Option[Uri]] = for {
      export <- readOne[Export](Exports.getExportWithStatus(exportId, user, ExportStatus.Exported))
      uri <- OptionT.fromOption[Future] { export.flatMap(_.getExportOptions.map(_.getSignedUrl(objectKey): Uri)) }.value
    } yield uri

    onComplete(x) { y =>
      y match {
        case Success(Some(z)) => redirect(z, StatusCodes.TemporaryRedirect)
        case _ => throw new Exception
      }
    }
  }
}
