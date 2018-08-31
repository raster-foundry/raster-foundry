package com.azavea.rf.api.tool

import com.azavea.rf.authentication.Authentication
import com.azavea.rf.common._
import com.azavea.rf.common.ast._
import com.azavea.rf.datamodel._
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.ast.codec._
import com.azavea.rf.database.filter.Filterables._
import io.circe._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import cats.implicits._
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import kamon.akka.http.KamonTraceDirectives
import java.util.UUID

import cats.effect.IO
import com.azavea.rf.database.ToolDao
import doobie._
import doobie.implicits._
import doobie.Fragments.in
import doobie.postgres._
import doobie.postgres.implicits._

trait ToolRoutes
    extends Authentication
    with ToolQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with KamonTraceDirectives
    with UserErrorHandler {

  val xa: Transactor[IO]

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
                deleteTool(toolId)
              }
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
          } ~
          pathPrefix("permissions") {
            pathEndOrSingleSlash {
              put {
                traceName("replace-tool-permissions") {
                  replaceToolPermissions(toolId)
                }
              }
            } ~
              post {
                traceName("add-tool-permission") {
                  addToolPermission(toolId)
                }
              } ~
              get {
                traceName("list-tool-permissions") {
                  listToolPermissions(toolId)
                }
              } ~
              delete {
                deleteToolPermissions(toolId)
              }
          } ~
          pathPrefix("actions") {
            pathEndOrSingleSlash {
              get {
                traceName("list-user-allowed-actions") {
                  listUserTemplateActions(toolId)
                }
              }
            }
          }
      }
  }

  def getToolSources(toolId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ToolDao
        .authorized(user, ObjectType.Template, toolId, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      rejectEmptyResponse {
        onSuccess(
          ToolDao.query
            .filter(toolId)
            .selectOption
            .transact(xa)
            .unsafeToFuture) { maybeTool =>
          val sources = maybeTool.map(
            _.definition.as[MapAlgebraAST].valueOr(throw _).sources)
          complete(sources)
        }
      }
    }
  }

  def listTools: Route = authenticate { user =>
    (withPagination & combinedToolQueryParams) {
      (page, combinedToolQueryParameters) =>
        complete {
          ToolDao
            .authQuery(
              user,
              ObjectType.Template,
              combinedToolQueryParameters.ownershipTypeParams.ownershipType,
              combinedToolQueryParameters.groupQueryParameters.groupType,
              combinedToolQueryParameters.groupQueryParameters.groupId
            )
            .filter(combinedToolQueryParameters)
            .page(page, fr"")
            .transact(xa)
            .unsafeToFuture
        }
    }
  }

  def createTool: Route = authenticate { user =>
    entity(as[Tool.Create]) { newTool =>
      onSuccess(ToolDao.insert(newTool, user).transact(xa).unsafeToFuture) {
        tool =>
          complete(StatusCodes.Created, tool)
      }
    }
  }

  def getTool(toolId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ToolDao
        .authorized(user, ObjectType.Template, toolId, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      rejectEmptyResponse {
        complete(
          ToolDao.query.filter(toolId).selectOption.transact(xa).unsafeToFuture)
      }
    }
  }

  def updateTool(toolId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ToolDao
        .authorized(user, ObjectType.Template, toolId, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[Tool]) { updatedTool =>
        onSuccess(
          ToolDao
            .update(updatedTool, toolId, user)
            .transact(xa)
            .unsafeToFuture) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteTool(toolId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ToolDao
        .authorized(user, ObjectType.Template, toolId, ActionType.Delete)
        .transact(xa)
        .unsafeToFuture
    } {
      onSuccess(ToolDao.query.filter(toolId).delete.transact(xa).unsafeToFuture) {
        completeSingleOrNotFound
      }
    }
  }

  def validateAST: Route = authenticate { user =>
    entity(as[Json]) { jsonAst =>
      {
        complete {
          jsonAst.as[MapAlgebraAST] match {
            case Right(ast) =>
              validateTree[Unit](ast)
              (StatusCodes.OK, ast)
            case Left(msg) =>
              (StatusCodes.BadRequest, "Unable to parse json as MapAlgebra AST")
          }
        }
      }
    }
  }

  def listToolPermissions(toolId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ToolDao.query.ownedBy(user, toolId).exists.transact(xa).unsafeToFuture
    } {
      complete {
        ToolDao
          .getPermissions(toolId)
          .transact(xa)
          .unsafeToFuture
      }
    }
  }

  def replaceToolPermissions(toolId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ToolDao.query.ownedBy(user, toolId).exists.transact(xa).unsafeToFuture
    } {
      entity(as[List[ObjectAccessControlRule]]) { acrList =>
        complete {
          ToolDao
            .replacePermissions(toolId, acrList)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def addToolPermission(toolId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ToolDao.query.ownedBy(user, toolId).exists.transact(xa).unsafeToFuture
    } {
      entity(as[ObjectAccessControlRule]) { acr =>
        complete {
          ToolDao
            .addPermission(toolId, acr)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def listUserTemplateActions(templateId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ToolDao
        .authorized(user, ObjectType.Template, templateId, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      user.isSuperuser match {
        case true => complete(List("*"))
        case false =>
          onSuccess(
            ToolDao.query.filter(templateId).select.transact(xa).unsafeToFuture
          ) { template =>
            template.owner == user.id match {
              case true => complete(List("*"))
              case false =>
                complete {
                  ToolDao
                    .listUserActions(user, templateId)
                    .transact(xa)
                    .unsafeToFuture
                }
            }
          }
      }
    }
  }

  def deleteToolPermissions(toolId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ToolDao.query.ownedBy(user, toolId).exists.transact(xa).unsafeToFuture
    } {
      complete {
        ToolDao
          .deletePermissions(toolId)
          .transact(xa)
          .unsafeToFuture
      }
    }
  }
}
