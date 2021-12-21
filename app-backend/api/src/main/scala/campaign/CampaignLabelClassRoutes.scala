package com.rasterfoundry.api.campaign

import com.rasterfoundry.akkautil._
import com.rasterfoundry.api.CommonLabelClassRoutes
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.server._
import cats.effect._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._

import java.util.UUID

trait CampaignLabelClassRoutes
    extends CommonHandlers
    with Directives
    with Authentication {

  val xa: Transactor[IO]

  def commonLabelClassRoutes: CommonLabelClassRoutes

  def listCampaignGroupLabelClasses(
      campaignId: UUID,
      labelClassGroupId: UUID
  ): Route =
    authenticate { case (user, _) =>
      authorizeScope(
        ScopedAction(Domain.Campaigns, Action.Read, None),
        user
      ) {
        authorizeAuthResultAsync {
          CampaignDao
            .authorized(
              user,
              ObjectType.AnnotationProject,
              campaignId,
              ActionType.Annotate
            )
            .transact(xa)
            .unsafeToFuture
        } {
          commonLabelClassRoutes.listLabelClasses(labelClassGroupId)
        }
      }
    }

  def addCampaignLabelClassToGroup(campaignId: UUID, groupId: UUID): Route =
    authenticate { case (user, _) =>
      authorizeScope(
        ScopedAction(Domain.Campaigns, Action.Update, None),
        user
      ) {
        authorizeAuthResultAsync {
          CampaignDao
            .authorized(
              user,
              ObjectType.AnnotationProject,
              campaignId,
              ActionType.Edit
            )
            .transact(xa)
            .unsafeToFuture
        } {
          entity(as[AnnotationLabelClass.Create]) { labelClassCreate =>
            commonLabelClassRoutes.addLabelClass(labelClassCreate, groupId)
          }
        }
      }
    }

  def getCampaignLabelClass(campaignId: UUID, labelClassId: UUID): Route =
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
                ObjectType.AnnotationProject,
                campaignId,
                ActionType.Annotate
              )
              .transact(xa)
              .unsafeToFuture
          } {
            commonLabelClassRoutes.getLabelClass(
              labelClassId
            )
          }
        }
      }
    }

  def updateCampaignLabelClass(campaignId: UUID, labelClassId: UUID): Route =
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
                ObjectType.AnnotationProject,
                campaignId,
                ActionType.Edit
              )
              .transact(xa)
              .unsafeToFuture
          } {
            entity(as[AnnotationLabelClass]) { updatedClass =>
              commonLabelClassRoutes.updateLabelClass(
                updatedClass,
                labelClassId
              )
            }
          }
        }
      }
    }

  def activateCampaignLabelClass(campaignId: UUID, labelClassId: UUID): Route =
    authenticate { case (user, _) =>
      authorizeScope(
        ScopedAction(Domain.Campaigns, Action.Update, None),
        user
      ) {
        authorizeAuthResultAsync {
          CampaignDao
            .authorized(
              user,
              ObjectType.AnnotationProject,
              campaignId,
              ActionType.Edit
            )
            .transact(xa)
            .unsafeToFuture
        } {
          commonLabelClassRoutes.activeLabelClass(labelClassId)
        }

      }
    }

  def deactivateCampaignLabelClass(
      campaignId: UUID,
      labelClassId: UUID
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
              ObjectType.AnnotationProject,
              campaignId,
              ActionType.Edit
            )
            .transact(xa)
            .unsafeToFuture
        } {
          commonLabelClassRoutes.deactivateLabelClass(labelClassId)
        }
      }
    }
}
