package com.rasterfoundry.api.tool

import com.rasterfoundry.akkautil._
import com.rasterfoundry.common.ast._
import com.rasterfoundry.common.ast.codec.MapAlgebraCodec._
import com.rasterfoundry.datamodel._
import com.rasterfoundry.database.filter.Filterables._
import com.rasterfoundry.database.ToolDao

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import cats.implicits._
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import cats.effect.IO
import doobie._
import doobie.implicits._

import java.util.UUID

trait ToolRoutes
    extends Authentication
    with ToolQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler {

  val xa: Transactor[IO]

  val toolRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get {
        listTools
      } ~
        post {
          createTool
        }
    } ~
      pathPrefix(JavaUUID) { toolId =>
        pathEndOrSingleSlash {
          get {
            getTool(toolId)
          } ~
            put {
              updateTool(toolId)
            } ~
            delete {
              deleteTool(toolId)
            }
        } ~
          pathPrefix("sources") {
            pathEndOrSingleSlash {
              get {
                getToolSources(toolId)
              }
            }
          } ~
          pathPrefix("permissions") {
            pathEndOrSingleSlash {
              put {
                replaceToolPermissions(toolId)
              }
            } ~
              post {
                addToolPermission(toolId)
              } ~
              get {
                listToolPermissions(toolId)
              } ~
              delete {
                deleteToolPermissions(toolId)
              }
          } ~
          pathPrefix("actions") {
            pathEndOrSingleSlash {
              get {
                listUserTemplateActions(toolId)
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
            .page(page)
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

  def listToolPermissions(toolId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ToolDao
        .authorized(user, ObjectType.Template, toolId, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
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
    entity(as[List[ObjectAccessControlRule]]) { acrList =>
      authorizeAsync {
        (ToolDao.authorized(user, ObjectType.Template, toolId, ActionType.Edit),
         acrList traverse { acr =>
           ToolDao.isValidPermission(acr, user)
         } map { _.foldLeft(true)(_ && _) }).tupled
          .map({ authTup =>
            authTup._1 && authTup._2
          })
          .transact(xa)
          .unsafeToFuture
      } {
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
    entity(as[ObjectAccessControlRule]) { acr =>
      authorizeAsync {
        (ToolDao.authorized(user, ObjectType.Template, toolId, ActionType.Edit),
         ToolDao.isValidPermission(acr, user)).tupled
          .map({ authTup =>
            authTup._1 && authTup._2
          })
          .transact(xa)
          .unsafeToFuture
      } {
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
      ToolDao
        .authorized(user, ObjectType.Template, toolId, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
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
