package com.rasterfoundry.api.campaign

import com.rasterfoundry.akkautil._
import com.rasterfoundry.api.CommonLabelClassGroupRoutes
import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import cats.effect._
import cats.implicits._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor

import scala.util.{Failure, Success}

import java.util.UUID

trait CampaignLabelClassGroupRoutes
    extends CommonHandlers
    with Directives
    with Authentication
    with PaginationDirectives
    with QueryParametersCommon {

  val xa: Transactor[IO]

  def commonLabelClassGroupRoutes: CommonLabelClassGroupRoutes

  def listCampaignLabelClassGroups(campaignId: UUID): Route =
    authenticate { case (user, _) =>
      authorizeScope(
        ScopedAction(Domain.Campaigns, Action.Read, None),
        user
      ) {
        authorizeAuthResultAsync {
          CampaignDao
            .authorized(
              user,
              ObjectType.Campaign,
              campaignId,
              ActionType.Annotate
            )
            .transact(xa)
            .unsafeToFuture
        } {
          complete {
            (
              AnnotationLabelClassGroupDao
                .listByCampaignIdWithClasses(campaignId)
              )
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }

  def createCampaignLabelClassGroup(campaignId: UUID): Route =
    authenticate { case (user, _) =>
      authorizeScope(
        ScopedAction(Domain.Campaigns, Action.Update, None),
        user
      ) {
        authorizeAuthResultAsync {
          CampaignDao
            .authorized(
              user,
              ObjectType.Campaign,
              campaignId,
              ActionType.Edit
            )
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[AnnotationLabelClassGroup.Create]) { classGroupCreate =>
            onComplete {
              (for {
                groups <- AnnotationLabelClassGroupDao.listByCampaignId(
                  campaignId)
                campaignOpt <- CampaignDao.getCampaignById(campaignId)
                created <- campaignOpt traverse { campaign =>
                  AnnotationLabelClassGroupDao.insertAnnotationLabelClassGroup(
                    classGroupCreate,
                    None,
                    Some(campaign),
                    groups.size // new class group should be appended to the end
                  )
                }
              } yield created)
                .transact(xa)
                .unsafeToFuture
            } {
              case Success(Some(groupsWithClasses)) =>
                complete { groupsWithClasses }
              case Success(None) =>
                complete {
                  StatusCodes.NotFound -> "Campaign does not exist"
                }
              case Failure(e) =>
                logger.error(e.getMessage)
                complete { HttpResponse(StatusCodes.BadRequest) }
            }
          }
        }
      }
    }

  def getCampaignLabelClassGroup(campaignId: UUID, classGroupId: UUID): Route =
    authenticate { case (user, _) =>
      authorizeScope(
        ScopedAction(Domain.Campaigns, Action.Read, None),
        user
      ) {
        {
          authorizeAuthResultAsync {
            CampaignDao
              .authorized(
                user,
                ObjectType.Campaign,
                campaignId,
                ActionType.Annotate
              )
              .transact(xa)
              .unsafeToFuture
          } {
            commonLabelClassGroupRoutes.getLabelClassGroup(classGroupId)
          }
        }
      }
    }

  def updateCampaignLabelClassGroup(
      campaignId: UUID,
      classGroupId: UUID
  ): Route =
    authenticate { case (user, _) =>
      authorizeScope(
        ScopedAction(Domain.Campaigns, Action.Update, None),
        user
      ) {
        {
          authorizeAuthResultAsync {
            CampaignDao
              .authorized(
                user,
                ObjectType.Campaign,
                campaignId,
                ActionType.Edit
              )
              .transact(xa)
              .unsafeToFuture
          } {
            entity(as[AnnotationLabelClassGroup]) { updatedClassGroup =>
              commonLabelClassGroupRoutes.updateLabelClassGroup(
                updatedClassGroup,
                classGroupId
              )
            }
          }
        }
      }
    }

  def activateCampaignLabelClassGroup(
      campaignId: UUID,
      classGroupId: UUID
  ): Route =
    authenticate { case (user, _) =>
      authorizeScope(
        ScopedAction(Domain.Campaigns, Action.Update, None),
        user
      ) {
        authorizeAuthResultAsync {
          CampaignDao
            .authorized(
              user,
              ObjectType.Campaign,
              campaignId,
              ActionType.Edit
            )
            .transact(xa)
            .unsafeToFuture
        } {
          commonLabelClassGroupRoutes.activateLabelClassGroup(classGroupId)
        }

      }
    }

  def deactivateCampaignLabelClassGroup(
      campaignId: UUID,
      classGroupId: UUID
  ): Route =
    authenticate { case (user, _) =>
      authorizeScope(
        ScopedAction(Domain.Campaigns, Action.Update, None),
        user
      ) {
        authorizeAuthResultAsync {
          CampaignDao
            .authorized(
              user,
              ObjectType.Campaign,
              campaignId,
              ActionType.Edit
            )
            .transact(xa)
            .unsafeToFuture
        } {
          commonLabelClassGroupRoutes.deactivateLabelClassGroup(classGroupId)
        }
      }
    }
}
