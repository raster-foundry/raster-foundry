package com.azavea.rf.api.toolrun

import com.azavea.rf.authentication.Authentication
import com.azavea.rf.common._
import com.azavea.rf.common.ast._
import com.azavea.rf.datamodel._
import com.azavea.rf.tool.ast.MapAlgebraAST
import com.azavea.rf.tool.eval.PureInterpreter
import com.azavea.rf.database.filter.Filterables._
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import cats.implicits._
import java.util.UUID

import cats.effect.IO
import com.azavea.rf.database.ToolRunDao
import doobie.util.transactor.Transactor

import scala.concurrent.ExecutionContext.Implicits.global

import doobie._
import doobie.implicits._
import doobie.Fragments.in
import doobie.postgres._
import doobie.postgres.implicits._

trait ToolRunRoutes
    extends Authentication
    with PaginationDirectives
    with ToolRunQueryParametersDirective
    with CommonHandlers
    with UserErrorHandler {

  val xa: Transactor[IO]

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
        } ~
          pathPrefix("permissions") {
            pathEndOrSingleSlash {
              put {
                replaceToolRunPermissions(runId)
              }
            } ~
              post {
                addToolRunPermission(runId)
              } ~
              get {
                listToolRunPermissions(runId)
              } ~
              delete {
                deleteToolRunPermissions(runId)
              }
          } ~
          pathPrefix("actions") {
            pathEndOrSingleSlash {
              get {
                listUserAnalysisActions(runId)
              }
            }
          }
      }
  }

  def listToolRuns: Route = authenticate { user =>
    (withPagination & toolRunQueryParameters) { (page, runParams) =>
      complete {
        ToolRunDao
          .authQuery(user,
                     ObjectType.Analysis,
                     runParams.ownershipTypeParams.ownershipType,
                     runParams.groupQueryParameters.groupType,
                     runParams.groupQueryParameters.groupId)
          .filter(runParams)
          .page(page, fr"")
          .transact(xa)
          .unsafeToFuture
      }
    }
  }

  def createToolRun: Route = authenticate { user =>
    entity(as[ToolRun.Create]) { newRun =>
      onSuccess(
        ToolRunDao.insertToolRun(newRun, user).transact(xa).unsafeToFuture) {
        toolRun =>
          {
            complete {
              (StatusCodes.Created, toolRun)
            }
          }
      }
    }
  }

  def getToolRun(runId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ToolRunDao
        .authorized(user, ObjectType.Analysis, runId, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      rejectEmptyResponse {
        complete(
          ToolRunDao.query
            .filter(runId)
            .selectOption
            .transact(xa)
            .unsafeToFuture)
      }
    }
  }

  def updateToolRun(runId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ToolRunDao
        .authorized(user, ObjectType.Analysis, runId, ActionType.Edit)
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[ToolRun]) { updatedRun =>
        onSuccess(
          ToolRunDao
            .updateToolRun(updatedRun, runId, user)
            .transact(xa)
            .unsafeToFuture) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteToolRun(runId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ToolRunDao
        .authorized(user, ObjectType.Analysis, runId, ActionType.Delete)
        .transact(xa)
        .unsafeToFuture
    } {
      onSuccess(
        ToolRunDao.query.filter(runId).delete.transact(xa).unsafeToFuture) {
        completeSingleOrNotFound
      }
    }
  }

  def listToolRunPermissions(toolRunId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ToolRunDao.query
        .ownedBy(user, toolRunId)
        .exists
        .transact(xa)
        .unsafeToFuture
    } {
      complete {
        ToolRunDao
          .getPermissions(toolRunId)
          .transact(xa)
          .unsafeToFuture
      }
    }
  }

  def replaceToolRunPermissions(toolRunId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ToolRunDao.query
        .ownedBy(user, toolRunId)
        .exists
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[List[ObjectAccessControlRule]]) { acrList =>
        complete {
          ToolRunDao
            .replacePermissions(toolRunId, acrList)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def addToolRunPermission(toolRunId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ToolRunDao.query
        .ownedBy(user, toolRunId)
        .exists
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[ObjectAccessControlRule]) { acr =>
        complete {
          ToolRunDao
            .addPermission(toolRunId, acr)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def listUserAnalysisActions(analysisId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ToolRunDao
        .authorized(user, ObjectType.Analysis, analysisId, ActionType.View)
        .transact(xa)
        .unsafeToFuture
    } {
      user.isSuperuser match {
        case true => complete(List("*"))
        case false =>
          onSuccess(
            ToolRunDao.query
              .filter(analysisId)
              .select
              .transact(xa)
              .unsafeToFuture
          ) { analysis =>
            analysis.owner == user.id match {
              case true => complete(List("*"))
              case false =>
                complete {
                  ToolRunDao
                    .listUserActions(user, analysisId)
                    .transact(xa)
                    .unsafeToFuture
                }
            }
          }
      }
    }
  }

  def deleteToolRunPermissions(toolRunId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ToolRunDao.query
        .ownedBy(user, toolRunId)
        .exists
        .transact(xa)
        .unsafeToFuture
    } {
      complete {
        ToolRunDao
          .deletePermissions(toolRunId)
          .transact(xa)
          .unsafeToFuture
      }
    }
  }
}
