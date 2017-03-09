package com.azavea.rf.api.toolrun

import java.util.UUID

import scala.util.{Success, Failure}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route

import com.azavea.rf.common.{Authentication, UserErrorHandler}
import com.azavea.rf.database.{ActionRunner, Database}
import com.azavea.rf.database.tables.ToolRuns
import com.azavea.rf.datamodel.{PaginatedResponse, ToolRun}
import com.lonelyplanet.akka.http.extensions.PaginationDirectives

import scala.concurrent.ExecutionContext.Implicits.global

trait ToolRunRoutes extends Authentication
    with PaginationDirectives
    with ToolRunQueryParametersDirective
    with UserErrorHandler
    with ActionRunner {
  implicit def database: Database

  val toolRunRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listToolRuns } ~
      post { createToolRun }
    } ~
    pathPrefix(JavaUUID) { runId =>
      pathEndOrSingleSlash {
        get { getToolRun(runId) } ~
        put { updateToolRun(runId) } ~
        delete { deleteToolRun(runId) }
      }
    }
  }

  def listToolRuns: Route = authenticate { user =>
    (withPagination & toolRunQueryParameters) { (page, runParams) =>
      complete {
        list(ToolRuns.listToolRuns(page.offset, page.limit, runParams), page.offset, page.limit)
      }
    }
  }

  def createToolRun: Route = authenticate { user =>
    entity(as[ToolRun.Create]) { newRun =>
      onSuccess(write(ToolRuns.insertToolRun(newRun, user.id))) { toolRun =>
        complete(StatusCodes.Created, toolRun)
      }
    }
  }

  def getToolRun(runId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete(readOne(ToolRuns.getToolRun(runId)))
    }
  }

  def updateToolRun(runId: UUID): Route = authenticate { user =>
    entity(as[ToolRun]) { updatedRun =>
      onComplete(update(ToolRuns.updateToolRun(updatedRun, runId, user))) {
        case Success(result) => {
          result match {
            case 1 => complete(StatusCodes.NoContent)
            case count => throw new IllegalStateException(
              s"Error updating tool run: update result expected to be 1, was $count"
            )
          }
        }
        case Failure(e) => throw e
      }
    }
  }

  def deleteToolRun(runId: UUID): Route = authenticate { user =>
    onSuccess(database.db.run(ToolRuns.deleteToolRun(runId))) {
      case 1 => complete(StatusCodes.NoContent)
      case 0 => complete(StatusCodes.NotFound)
      case count => throw new IllegalStateException(
        s"Error deleting tool run: delete result expected to be 1, was $count"
      )
    }
  }
}
