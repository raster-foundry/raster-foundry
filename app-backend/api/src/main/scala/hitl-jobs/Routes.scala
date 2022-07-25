package com.rasterfoundry.api.hitlJobs

import com.rasterfoundry.akkautil._
import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.common.AWSBatch
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import cats.effect.IO
import cats.implicits._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._

import java.util.UUID

trait HITLJobRoutes
    extends Authentication
    with PaginationDirectives
    with UserErrorHandler
    with CommonHandlers
    with QueryParametersCommon {
  val xa: Transactor[IO]

  val hitlJobRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listHITLJobs } ~
        post { createHITLJob }
    } ~
      pathPrefix(JavaUUID) { jobId =>
        pathEndOrSingleSlash {
          get { getHITLJob(jobId) } ~ put { updateHITLJob(jobId) } ~
            delete { deleteHITLJob(jobId) }
        }
      }
  }

  def listHITLJobs: Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Campaigns, Action.Read, None),
        user
      ) {
        (withPagination & hitlJobQueryParameters) {
          (page: PageRequest, params: HITLJobQueryParameters) =>
            complete {
              HITLJobDao
                .list(page, params, user)
                .transact(xa)
                .unsafeToFuture
            }
        }
      }
    }

  def createHITLJob: Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Campaigns, Action.Create, None),
        user
      ) {
        entity(as[HITLJob.Create]) { newHITLJob =>
          ((authorizeAsync {
            (
              CampaignDao.isActiveCampaign(newHITLJob.campaignId),
              HITLJobDao.hasInProgressJob(
                newHITLJob.campaignId,
                newHITLJob.projectId,
                user
              ),
              CampaignDao
                .authorized(
                  user,
                  ObjectType.Campaign,
                  newHITLJob.campaignId,
                  ActionType.View
                ),
              AnnotationProjectDao.authorized(
                user,
                ObjectType.AnnotationProject,
                newHITLJob.projectId,
                ActionType.View
              )
            ).tupled.transact(xa).unsafeToFuture map {
              case (true, false, campaignResult, annotationProjectResult) =>
                campaignResult.toBoolean || annotationProjectResult.toBoolean
              case _ => false
            }
          })) {
            onSuccess(
              HITLJobDao
                .create(newHITLJob, user)
                .transact(xa)
                .unsafeToFuture
            ) { hitlJob =>
              kickoffHITL(hitlJob.id)
              complete((StatusCodes.Created, hitlJob))
            }
          }
        }
      }
    }

  def getHITLJob(id: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Campaigns, Action.Read, None),
        user
      ) {
        authorizeAsync {
          HITLJobDao
            .isOwnerOrSuperUser(user, id)
            .transact(xa)
            .unsafeToFuture
        } {
          rejectEmptyResponse {
            complete {
              HITLJobDao
                .getById(id)
                .transact(xa)
                .unsafeToFuture
            }
          }
        }
      }
    }

  def updateHITLJob(id: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Campaigns, Action.Update, None),
        user
      ) {
        authorizeAsync {
          HITLJobDao
            .isOwnerOrSuperUser(user, id)
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[HITLJob]) { hitlJobToUpdate =>
            onSuccess(
              HITLJobDao
                .update(
                  hitlJobToUpdate,
                  id
                )
                .transact(xa)
                .unsafeToFuture
            ) {
              completeSingleOrNotFound
            }
          }
        }
      }
    }

  def deleteHITLJob(id: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Campaigns, Action.Delete, None),
        user
      ) {
        authorizeAsync {
          HITLJobDao
            .isOwnerOrSuperUser(user, id)
            .transact(xa)
            .unsafeToFuture
        } {
          onSuccess(
            HITLJobDao
              .delete(id)
              .transact(xa)
              .unsafeToFuture
          ) { count: Int =>
            complete((StatusCodes.NoContent, s"$count HITL job deleted"))
          }
        }
      }
    }
}
