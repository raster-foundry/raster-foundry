package com.azavea.rf.api.tool

import java.util.UUID

import scala.util.{Success, Failure}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.azavea.rf.common.{Authentication, UserErrorHandler}
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.Tools
import com.azavea.rf.datamodel._
import com.lonelyplanet.akka.http.extensions.PaginationDirectives

trait ToolRoutes extends Authentication with PaginationDirectives with UserErrorHandler {
  implicit def database: Database

  val toolRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listTools } ~
      post { createTool }
    } ~
    pathPrefix(JavaUUID) { toolId =>
      pathEndOrSingleSlash {
        get { getTool(toolId) } ~
        put { updateTool(toolId) } ~
        delete { deleteTool(toolId) }
      }
    }
  }

  def listTools: Route = authenticate { user =>
    (withPagination) { (page) =>
      complete {
        Tools.listTools(page)
      }
    }
  }

  def createTool: Route = authenticate { user =>
    entity(as[Tool.Create]) { newTool =>
      onSuccess(Tools.insertTool(newTool, user.id)) { tool =>
        complete(StatusCodes.Created, tool)
      }
    }
  }

  def getTool(toolId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete(Tools.getTool(toolId))
    }
  }

  def updateTool(toolId: UUID): Route = authenticate { user =>
    entity(as[Tool]) { updatedTool =>
      onComplete(Tools.updateTool(updatedTool, toolId, user)) {
        case Success(result) => {
          result match {
            case 1 => complete(StatusCodes.NoContent)
            case count => throw new IllegalStateException(
              s"Error updating tool: update result expected to be 1, was $count"
            )
          }
        }
        case Failure(e) => throw e
      }
    }
  }

  def deleteTool(toolId: UUID): Route = authenticate { user =>
    onSuccess(Tools.deleteTool(toolId)) {
      case 1 => complete(StatusCodes.NoContent)
      case 0 => complete(StatusCodes.NotFound)
      case count => throw new IllegalStateException(
        s"Error deleting tool: delete result expected to be 1, was $count"
      )
    }
  }

}
