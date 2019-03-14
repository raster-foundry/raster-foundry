package com.rasterfoundry.api.toolrun

import com.rasterfoundry.akkautil._
import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.database.filter.Filterables._
import com.rasterfoundry.database.{ToolRunDao, UserDao}
import com.rasterfoundry.api.project.ProjectAuthorizationDirectives
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import cats.implicits._
import cats.effect.IO
import doobie.util.transactor.Transactor
import doobie._
import doobie.implicits._
import java.util.UUID

trait ToolRunRoutes
    extends Authentication
    with PaginationDirectives
    with ToolRunQueryParametersDirective
    with ToolRunAuthorizationDirective
    with ProjectAuthorizationDirectives
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

  // This could probably be written cleaner, but I just want to get things working so I can work on the primary task
  // of getting the share page working correctly
  def listToolRuns: Route = (withPagination & toolRunQueryParameters) {
    (page, runParams) =>
      runParams.toolRunParams.projectId match {
        case Some(projectId) =>
          (extractTokenHeader & extractMapTokenParam) { (tokenO, mapTokenO) =>
            (
              toolRunAuthProjectFromMapTokenO(mapTokenO, projectId) |
                projectAuthFromTokenO(tokenO, projectId)
            ) {
              complete {
                val userOQuery
                  : Option[doobie.ConnectionIO[User]] = tokenO flatMap {
                  token: String =>
                    verifyJWT(token.split(" ").last).toOption
                } map {
                  case (_, jwtClaims) =>
                    val userId = jwtClaims.getStringClaim("sub")
                    UserDao.unsafeGetUserById(userId)
                }
                val analysesQuery = userOQuery match {
                  case Some(userQ) =>
                    userQ.flatMap(
                      user =>
                        ToolRunDao.listAnalysesWithRelated(
                          Some(user),
                          page,
                          projectId,
                          runParams.ownershipTypeParams.ownershipType,
                          runParams.groupQueryParameters.groupType,
                          runParams.groupQueryParameters.groupId
                      ))
                  case _ =>
                    ToolRunDao.listAnalysesWithRelated(
                      None,
                      page,
                      projectId,
                      None,
                      None,
                      None
                    )
                }
                analysesQuery.transact(xa).unsafeToFuture
              }
            }
          }
        case _ =>
          authenticate { user =>
            complete {
              ToolRunDao
                .authQuery(user,
                           ObjectType.Analysis,
                           runParams.ownershipTypeParams.ownershipType,
                           runParams.groupQueryParameters.groupType,
                           runParams.groupQueryParameters.groupId)
                .filter(runParams)
                .page(page)
                .transact(xa)
                .unsafeToFuture

            }
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
      ToolRunDao
        .authorized(user, ObjectType.Analysis, toolRunId, ActionType.Edit)
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
    entity(as[List[ObjectAccessControlRule]]) { acrList =>
      authorizeAsync {
        (ToolRunDao.authorized(user,
                               ObjectType.Analysis,
                               toolRunId,
                               ActionType.Edit),
         acrList traverse { acr =>
           ToolRunDao.isValidPermission(acr, user)
         } map { _.foldLeft(true)(_ && _) }).tupled
          .map({ authTup =>
            authTup._1 && authTup._2
          })
          .transact(xa)
          .unsafeToFuture
      } {
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
    entity(as[ObjectAccessControlRule]) { acr =>
      authorizeAsync {
        (ToolRunDao
           .authorized(user, ObjectType.Analysis, toolRunId, ActionType.Edit),
         ToolRunDao.isValidPermission(acr, user)).tupled
          .map({ authTup =>
            authTup._1 && authTup._2
          })
          .transact(xa)
          .unsafeToFuture
      } {
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
        .authorized(user, ObjectType.Analysis, analysisId, ActionType.Edit)
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
      ToolRunDao
        .authorized(user, ObjectType.Analysis, toolRunId, ActionType.Edit)
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
