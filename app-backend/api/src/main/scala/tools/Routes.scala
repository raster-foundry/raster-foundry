package com.azavea.rf.api.tool

import java.util.UUID

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.azavea.rf.common.{Authentication, CommonHandlers, UserErrorHandler}
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.Tools
import com.azavea.rf.datamodel._
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import de.heikoseeberger.akkahttpcirce.CirceSupport._

trait ToolRoutes extends Authentication
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler {

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
        Tools.listTools(page, user)
      }
    }
  }

  def createTool: Route = authenticate { user =>
    entity(as[Tool.Create]) { newTool =>
      authorize(user.isInRootOrSameOrganizationAs(newTool)) {
        onSuccess(Tools.insertTool(newTool, user)) { tool =>
          complete(StatusCodes.Created, tool)
        }
      }
    }
  }

  def getTool(toolId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete(Tools.getTool(toolId, user))
    }
  }

  def updateTool(toolId: UUID): Route = authenticate { user =>
    entity(as[Tool]) { updatedTool =>
      authorize(user.isInRootOrSameOrganizationAs(updatedTool)) {
        onSuccess(Tools.updateTool(updatedTool, toolId, user)) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteTool(toolId: UUID): Route = authenticate { user =>
    onSuccess(Tools.deleteTool(toolId, user)) {
      completeSingleOrNotFound
    }
  }

}
