package com.azavea.rf.api.workspace

import com.azavea.rf.common._
import com.azavea.rf.common.ast._
import com.azavea.rf.datamodel._
import com.azavea.rf.tool.ast.MapAlgebraAST
import com.azavea.rf.tool.eval.PureInterpreter
import com.azavea.rf.database.filter.Filterables._
import com.azavea.maml.serve.InterpreterExceptionHandling
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import cats.implicits._
import java.util.UUID

import cats.effect.IO
import com.azavea.rf.database._
import doobie.util.transactor.Transactor

import scala.concurrent.ExecutionContext.Implicits.global

import doobie._
import doobie.implicits._

import doobie.Fragments.in
import doobie.postgres._
import doobie.postgres.implicits._



trait WorkspaceRoutes extends Authentication
    with WorkspaceQueryParametersDirective
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler
    with InterpreterExceptionHandling {

  val xa: Transactor[IO]

  val workspaceRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listWorkspaces } ~
      post { createWorkspace }
    } ~
    pathPrefix(JavaUUID) { workspaceId =>
      pathEndOrSingleSlash {
        get { getWorkspace(workspaceId) } ~
        put { updateWorkspace(workspaceId) } ~
        delete { deleteWorkspace(workspaceId) }
      } ~
      pathPrefix("analyses") {
        pathEndOrSingleSlash {
          get { listWorkspaceAnalyses(workspaceId) } ~
          post { createWorkspaceAnalysis(workspaceId) }
        } ~
        pathPrefix(JavaUUID) { analysisId =>
          pathEndOrSingleSlash {
            delete { deleteWorkspaceAnalysis(workspaceId, analysisId) }
          }
        }
      }
    }
  }

  def listWorkspaces: Route = authenticate { user =>
    (withPagination & workspaceQueryParameters) { (page, workspaceParams) =>
      complete {
        WorkspaceWithRelatedDao
          .listWorkspaces(page, workspaceParams, user)
          .transact(xa).unsafeToFuture
      }
    }
  }

  def createWorkspace: Route = authenticate { user =>
    entity(as[Workspace.Create]) { newWorkspace =>
      authorize(user.isInRootOrSameOrganizationAs(newWorkspace)) {
        onSuccess(WorkspaceDao.insert(newWorkspace, user).transact(xa).unsafeToFuture) { workspace =>
          complete(StatusCodes.Created, workspace)
        }
      }
    }
  }

  def getWorkspace(workspaceId: UUID): Route = authenticate { user =>
    complete {
      WorkspaceWithRelatedDao.getById(workspaceId, user)
        .transact(xa)
        .unsafeToFuture
    }
  }

  def updateWorkspace(workspaceId: UUID): Route = authenticate { user =>
    entity(as[Workspace.Update]) { workspaceUpdate =>
      authorize(user.isInRootOrSameOrganizationAs(workspaceUpdate)) {
        onSuccess(
          WorkspaceDao.query
            .filter(fr"id = ${workspaceId}")
            .ownerFilter(user)
            .selectOption
            .flatMap( workspace =>
              workspace match {
                case Some(workspace) =>
                  WorkspaceDao.update(workspaceUpdate, workspace.id, user).flatMap( update =>
                    update match {
                      case 1 =>
                        WorkspaceTagDao
                          .setWorkspaceTags(workspace, workspaceUpdate.tags)
                          .map(_ => update)
                      case _ =>
                        0.pure[ConnectionIO]
                    }
                  )
                case None =>
                  0.pure[ConnectionIO]
              }
            ).transact(xa).unsafeToFuture
        ) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteWorkspace(workspaceId: UUID): Route = authenticate { user =>
    onSuccess(
      WorkspaceDao.query
        .filter(fr"id = ${workspaceId}")
        .ownerFilter(user)
        .delete
        .transact(xa).unsafeToFuture
    ) {
      completeSingleOrNotFound
    }
  }

  def createWorkspaceAnalysis(workspaceId: UUID): Route = authenticate { user =>
    entity(as[Analysis.Create]) { analysisCreate =>
      rejectEmptyResponse {
        complete {
          WorkspaceDao.addAnalysis(workspaceId, analysisCreate, user)
            .transact(xa).unsafeToFuture
        }
      }
    }
  }

  def deleteWorkspaceAnalysis(workspaceId: UUID, analysisId: UUID): Route = authenticate { user =>
    onSuccess(
      WorkspaceDao.deleteAnalysis(workspaceId, analysisId, user)
        .transact(xa).unsafeToFuture
    ) {
      completeSingleOrNotFound
    }
  }

  def listWorkspaceAnalyses(workspaceId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        WorkspaceDao.getAnalyses(workspaceId, user)
          .transact(xa).unsafeToFuture
      }
    }
  }
}
