package com.rasterfoundry.api.campaign

import com.rasterfoundry.akkautil._
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.server._
import cats.effect.IO
import cats.implicits._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor

import java.util.UUID

trait CampaignPermissionRoutes
    extends CommonHandlers
    with Directives
    with Authentication {

  val xa: Transactor[IO]

  def listCampaignPermissions(campaignId: UUID): Route = authenticate { user =>
    authorizeScope(
      ScopedAction(Domain.Campaigns, Action.ReadPermissions, None),
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
        complete {
          CampaignDao
            .getPermissions(campaignId)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def replaceCampaignPermissions(campaignId: UUID): Route = authenticate {
    user =>
      authorizeScope(
        ScopedAction(Domain.Campaigns, Action.Share, None),
        user
      ) {
        entity(as[List[ObjectAccessControlRule]]) { acrList =>
          authorizeAsync {
            (for {
              auth1 <- CampaignDao.authorized(
                user,
                ObjectType.Campaign,
                campaignId,
                ActionType.Edit
              )
              auth2 <- acrList traverse { acr =>
                CampaignDao.isValidPermission(acr, user)
              }
            } yield {
              auth1.toBoolean && (auth2.foldLeft(true)(_ && _) match {
                case true =>
                  CampaignDao.isReplaceWithinScopedLimit(
                    Domain.Campaigns,
                    user,
                    acrList
                  )
                case _ => false
              })
            }).transact(xa).unsafeToFuture()
          } {
            complete {
              CampaignDao
                .replacePermissions(campaignId, acrList)
                .transact(xa)
                .unsafeToFuture
            }
          }
        }
      }
  }

  def addCampaignPermission(campaignId: UUID): Route = authenticate { user =>
    val shareCount =
      CampaignDao
        .getShareCount(campaignId, user.id)
        .transact(xa)
        .unsafeToFuture
    authorizeScopeLimit(
      shareCount,
      Domain.Campaigns,
      Action.Share,
      user
    ) {
      entity(as[ObjectAccessControlRule]) { acr =>
        authorizeAsync {
          (for {
            auth1 <- CampaignDao.authorized(
              user,
              ObjectType.Campaign,
              campaignId,
              ActionType.Edit
            )
            auth2 <- CampaignDao.isValidPermission(acr, user)
          } yield {
            auth1.toBoolean && auth2
          }).transact(xa).unsafeToFuture()
        } {
          complete {
            CampaignDao
              .addPermission(campaignId, acr)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }
  }

  def deleteCampaignPermissions(campaignId: UUID): Route = authenticate {
    user =>
      authorizeScope(
        ScopedAction(Domain.Campaigns, Action.Share, None),
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
          complete {
            CampaignDao
              .deletePermissions(campaignId)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
  }

}
