package com.azavea.rf.api.tool

import com.azavea.rf.common._
import com.azavea.rf.common.ast._
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.Tools
import com.azavea.rf.datamodel._
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.ast.codec._

import io.circe._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import cats.implicits._
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import kamon.akka.http.KamonTraceDirectives
import kamon.akka.http.KamonTraceDirectives.traceName

import java.util.UUID


trait ToolRoutes extends Authentication
    with PaginationDirectives
    with CommonHandlers
    with KamonTraceDirectives
    with InterpreterExceptionHandling
    with UserErrorHandler {

  implicit def database: Database

  val toolRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get {
        traceName("tools-list") {
          listTools
        }
      } ~
      post {
        traceName("tools-create") {
          createTool
        }
      }
    } ~
    pathPrefix("validate") {
      post {
        traceName("ast-validate") {
          validateAST
        }
      }
    } ~
    pathPrefix(JavaUUID) { toolId =>
      pathEndOrSingleSlash {
        get {
          traceName("tools-detail") {
            getTool(toolId)
          }
        } ~
        put {
          traceName("tools-update") {
            updateTool(toolId)
          }
        } ~
        delete {
          traceName("tools-delete") {
            deleteTool(toolId) }
        }
      } ~
      pathPrefix("sources") {
        pathEndOrSingleSlash {
          get {
            traceName("tools-sources") {
              getToolSources(toolId)
            }
          }
        }
      }
    }
  }

  def getToolSources(toolId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      onSuccess(Tools.getTool(toolId, user)) { maybeTool =>
        val sources = maybeTool.map(_.definition.as[MapAlgebraAST].valueOr(throw _).sources)
        complete(sources)
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

  def validateAST: Route = authenticate { user =>
    entity(as[Json]) { ast =>
      handleExceptions(interpreterExceptionHandler) {
        complete {
          validateOnlyAST[Unit](ast)
          (StatusCodes.OK, ast)
        }
      }
    }
  }

}
