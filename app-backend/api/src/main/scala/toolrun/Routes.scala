package com.azavea.rf.api.toolrun

import com.azavea.rf.common._
import com.azavea.rf.common.ast._
import com.azavea.rf.database.{ActionRunner, Database}
import com.azavea.rf.database.tables.ToolRuns
import com.azavea.rf.datamodel._

import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import cats.implicits._

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global


trait ToolRunRoutes extends Authentication
    with PaginationDirectives
    with ToolRunQueryParametersDirective
    with CommonHandlers
    with UserErrorHandler
    with InterpreterExceptionHandling
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
        list(ToolRuns.listToolRuns(page.offset, page.limit, runParams, user), page.offset, page.limit)
      }
    }
  }

  def createToolRun: Route = authenticate { user =>
    entity(as[ToolRun.Create]) { newRun =>
      authorize(user.isInRootOrSameOrganizationAs(newRun)) {
        onSuccess(write(ToolRuns.insertToolRun(newRun, user))) { toolRun =>
          handleExceptions(interpreterExceptionHandler) {
            complete {
              validateAST[Unit](toolRun.id, user).map(_ => (StatusCodes.Created, toolRun))
            }
          }
        }
      }
    }
  }

  def getToolRun(runId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete(readOne(ToolRuns.getToolRun(runId, user)))
    }
  }

  def updateToolRun(runId: UUID): Route = authenticate { user =>
    entity(as[ToolRun]) { updatedRun =>
      authorize(user.isInRootOrSameOrganizationAs(updatedRun)) {
        onSuccess(update(ToolRuns.updateToolRun(updatedRun, runId, user))) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteToolRun(runId: UUID): Route = authenticate { user =>
    onSuccess(database.db.run(ToolRuns.deleteToolRun(runId, user))) {
      completeSingleOrNotFound
    }
  }
}
