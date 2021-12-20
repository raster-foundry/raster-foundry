package com.rasterfoundry.api.tool

import com.rasterfoundry.akkautil.PaginationDirectives
import com.rasterfoundry.akkautil._
import com.rasterfoundry.common.ast._
import com.rasterfoundry.common.ast.codec.MapAlgebraCodec._
import com.rasterfoundry.database.ToolDao
import com.rasterfoundry.database.filter.Filterables._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import cats.effect.IO
import cats.implicits._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._

import scala.concurrent.ExecutionContext

import java.util.UUID

trait ToolRoutes
    extends Authentication
    with ToolQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler {

  val xa: Transactor[IO]
  implicit val ec: ExecutionContext

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

  def getToolSources(toolId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Templates, Action.Read, None), user) {
      authorizeAuthResultAsync {
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
              .unsafeToFuture
          ) { maybeTool =>
            val sources = maybeTool.map(
              _.definition.as[MapAlgebraAST].valueOr(throw _).sources
            )
            complete(sources)
          }
        }
      }
    }
  }

  def listTools: Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Templates, Action.Read, None), user) {
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
  }

  def createTool: Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Templates, Action.Create, None), user) {
      entity(as[Tool.Create]) { newTool =>
        onSuccess(ToolDao.insert(newTool, user).transact(xa).unsafeToFuture) {
          tool =>
            complete(StatusCodes.Created, tool)
        }
      }
    }
  }

  def getTool(toolId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Templates, Action.Read, None), user) {
      authorizeAuthResultAsync {
        ToolDao
          .authorized(user, ObjectType.Template, toolId, ActionType.View)
          .transact(xa)
          .unsafeToFuture
      } {
        rejectEmptyResponse {
          complete(
            ToolDao.query
              .filter(toolId)
              .selectOption
              .transact(xa)
              .unsafeToFuture
          )
        }
      }
    }
  }

  def updateTool(toolId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Templates, Action.Update, None), user) {
      authorizeAuthResultAsync {
        ToolDao
          .authorized(user, ObjectType.Template, toolId, ActionType.Edit)
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[Tool]) { updatedTool =>
          onSuccess(
            ToolDao
              .update(updatedTool, toolId)
              .transact(xa)
              .unsafeToFuture
          ) {
            completeSingleOrNotFound
          }
        }
      }
    }
  }

  def deleteTool(toolId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Templates, Action.Delete, None), user) {
      authorizeAuthResultAsync {
        ToolDao
          .authorized(user, ObjectType.Template, toolId, ActionType.Delete)
          .transact(xa)
          .unsafeToFuture
      } {
        onSuccess(
          ToolDao.query.filter(toolId).delete.transact(xa).unsafeToFuture
        ) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def listToolPermissions(toolId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(
      ScopedAction(Domain.Templates, Action.ReadPermissions, None),
      user
    ) {
      authorizeAuthResultAsync {
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
  }

  def replaceToolPermissions(toolId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Templates, Action.Share, None), user) {
      entity(as[List[ObjectAccessControlRule]]) { acrList =>
        authorizeAsync {
          (
            ToolDao.authorized(
              user,
              ObjectType.Template,
              toolId,
              ActionType.Edit
            ) map {
              _.toBoolean
            },
            acrList traverse { acr =>
              ToolDao.isValidPermission(acr, user)
            } map {
              _.foldLeft(true)(_ && _)
            } map {
              case true =>
                ToolDao.isReplaceWithinScopedLimit(
                  Domain.Templates,
                  user,
                  acrList
                )
              case _ => false
            }
          ).tupled
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
  }

  def addToolPermission(toolId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Templates, Action.Share, None), user) {
      entity(as[ObjectAccessControlRule]) { acr =>
        authorizeAsync {
          (
            ToolDao.authorized(
              user,
              ObjectType.Template,
              toolId,
              ActionType.Edit
            ) map {
              _.toBoolean
            },
            ToolDao.isValidPermission(acr, user)
          ).tupled
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
  }

  def listUserTemplateActions(templateId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(
      ScopedAction(Domain.Templates, Action.ReadPermissions, None),
      user
    ) {
      authorizeAuthResultAsync {
        ToolDao
          .authorized(user, ObjectType.Template, templateId, ActionType.View)
          .transact(xa)
          .unsafeToFuture
      } {
        user.isSuperuser match {
          case true => complete(List("*"))
          case false =>
            onSuccess(
              ToolDao.query
                .filter(templateId)
                .select
                .transact(xa)
                .unsafeToFuture
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
  }

  def deleteToolPermissions(toolId: UUID): Route = authenticate { case MembershipAndUser(_, user) =>
    authorizeScope(ScopedAction(Domain.Templates, Action.Share, None), user) {
      authorizeAuthResultAsync {
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
}
