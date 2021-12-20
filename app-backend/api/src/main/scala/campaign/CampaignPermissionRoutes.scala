package com.rasterfoundry.api.campaign

import com.rasterfoundry.akkautil._
import com.rasterfoundry.api.user.Auth0Service
import com.rasterfoundry.api.utils.{Config, IntercomNotifications}
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.server._
import cats.data.OptionT
import cats.effect.{ContextShift, IO}
import cats.implicits._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.circe.syntax._

import scala.concurrent.Future

import java.util.UUID

trait CampaignPermissionRoutes
    extends CommonHandlers
    with Directives
    with Authentication
    with Config {

  implicit def contextShift: ContextShift[IO]

  def notifier: IntercomNotifications

  val xa: Transactor[IO]

  def listCampaignPermissions(campaignId: UUID): Route =
    authenticate { case MembershipAndUser(_, user) =>
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

  def replaceCampaignPermissions(campaignId: UUID): Route =
    authenticate { case MembershipAndUser(_, user) =>
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
            val distinctUserIds =
              acrList
                .foldMap(acr => acr.getUserId map { Set(_) })
                .combineAll
                .toList
            complete {
              (CampaignDao
                .replacePermissions(campaignId, acrList)
                .transact(xa) <* (
                CampaignDao
                  .unsafeGetCampaignById(campaignId)
                  .transact(xa) flatMap { campaign =>
                  distinctUserIds traverse { userId =>
                    UserDao.unsafeGetUserById(userId).transact(xa) flatMap {
                      sharedUser =>
                        notifier.shareNotify(
                          sharedUser,
                          user,
                          campaign,
                          "campaign"
                        )
                    }

                  }
                }
              )).unsafeToFuture
            }
          }
        }
      }
    }

  def addCampaignPermission(campaignId: UUID): Route =
    authenticate { case MembershipAndUser(_, user) =>
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
              (CampaignDao
                .addPermission(campaignId, acr)
                .transact(xa) <* (
                CampaignDao
                  .unsafeGetCampaignById(campaignId)
                  .transact(xa) flatMap { campaign =>
                  acr.getUserId traverse { userId =>
                    UserDao.unsafeGetUserById(userId).transact(xa) flatMap {
                      sharedUser =>
                        notifier.shareNotify(
                          sharedUser,
                          user,
                          campaign,
                          "campaign"
                        )
                    }
                  }
                }
              )).unsafeToFuture
            }
          }
        }
      }
    }

  def deleteCampaignPermissions(campaignId: UUID): Route =
    authenticate { case MembershipAndUser(_, user) =>
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

  def listCampaignShares(campaignId: UUID): Route =
    authenticate { case MembershipAndUser(_, user) =>
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
              .getSharedUsers(campaignId)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }

  def deleteCampaignShare(campaignId: UUID, deleteId: String): Route =
    authenticate { case MembershipAndUser(_, user) =>
      authorizeScope(ScopedAction(Domain.Campaigns, Action.Read, None), user) {
        if (user.id == deleteId) {
          authorizeAuthResultAsync {
            CampaignDao
              .authorized(
                user,
                ObjectType.Campaign,
                campaignId,
                ActionType.View
              )
              .transact(xa)
              .unsafeToFuture
          }
        } else {
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
          }
        }
        {
          complete {
            CampaignDao
              .deleteSharedUser(campaignId, deleteId)
              .map(c => if (c > 0) 1 else 0)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }

  def shareCampaign(campaignId: UUID): Route =
    authenticate { case MembershipAndUser(_, user) =>
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
        entity(as[UserShareInfo]) { userByEmail =>
          authorize {
            (userByEmail.actionType match {
              case Some(ActionType.Annotate) | Some(ActionType.Validate) |
                  None =>
                true
              case _ => false
            })
          } {
            complete {
              Auth0Service.getManagementBearerToken flatMap { managementToken =>
                for {
                  // Everything has to be Futures here because of methods in akka-http / Auth0Service
                  users <- UserDao
                    .findUsersByEmail(userByEmail.email)
                    .transact(xa)
                    .unsafeToFuture
                  userPlatform <- UserDao
                    .unsafeGetUserPlatform(user.id)
                    .transact(xa)
                    .unsafeToFuture
                  campaignO <- CampaignDao
                    .getCampaignById(campaignId)
                    .transact(xa)
                    .unsafeToFuture
                  permissions <- users match {
                    case Nil =>
                      for {
                        auth0User <- OptionT {
                          Auth0Service.findGroundworkUser(
                            userByEmail.email,
                            managementToken
                          )
                        } getOrElseF {
                          Auth0Service
                            .createGroundworkUser(
                              userByEmail.email,
                              managementToken
                            )
                        }
                        newUserOpt <- (auth0User.user_id traverse { userId =>
                          for {
                            user <- UserDao.create(
                              User.Create(
                                userId,
                                email = userByEmail.email,
                                scope = Scopes.GroundworkUser
                              )
                            )
                            _ <- UserGroupRoleDao.createDefaultRoles(user)
                            _ <- AnnotationProjectDao.copyProject(
                              UUID.fromString(groundworkSampleProject),
                              user
                            )
                          } yield user
                        }).transact(xa).unsafeToFuture
                        acrs = newUserOpt map { newUser =>
                          notifier.getDefaultShare(
                            newUser,
                            userByEmail.actionType
                          )
                        } getOrElse Nil
                        _ <- (newUserOpt, campaignO).tupled traverse {
                          case (newUser, campaign) =>
                            // if silent param is not provided, we notify
                            val isSilent = userByEmail.silent.getOrElse(false)
                            if (!isSilent) {
                              notifier.shareNotifyNewUser(
                                managementToken,
                                user,
                                userByEmail.email,
                                newUser.id,
                                userPlatform,
                                campaign,
                                "campaign",
                                Notifications.getInvitationMessage
                              )
                            } else {
                              Future.unit
                            }
                        }
                        // this is not an existing user,
                        // there is no project specific ACR yet,
                        // so no need to remove Validate action if only want Annotate
                        dbAcrs <- (acrs traverse { acr =>
                          CampaignDao
                            .addPermission(campaignId, acr)
                        }).transact(xa).unsafeToFuture
                      } yield dbAcrs
                    case existingUsers =>
                      existingUsers traverse { existingUser =>
                        val acrs =
                          notifier.getDefaultShare(
                            existingUser,
                            userByEmail.actionType
                          )
                        Auth0Service
                          .addUserMetadata(
                            existingUser.id,
                            managementToken,
                            Map(
                              "app_metadata" -> Map("annotateApp" -> true)
                            ).asJson
                          ) *>
                          (CampaignDao
                            .handleSharedPermissions(
                              campaignId,
                              existingUser.id,
                              acrs,
                              userByEmail.actionType
                            )
                            .transact(xa) <* (
                            // if silent param is not provided, we notify
                            userByEmail.silent match {
                              case Some(false) | None =>
                                CampaignDao
                                  .unsafeGetCampaignById(campaignId)
                                  .transact(xa) flatMap { campaign =>
                                  notifier.shareNotify(
                                    existingUser,
                                    user,
                                    campaign,
                                    "campaign"
                                  )
                                }
                              case _ => IO.pure(())
                            }
                          )).unsafeToFuture

                      } map { _.flatten }
                  }
                } yield permissions
              }
            }
          }
        }
      }
    }
}
