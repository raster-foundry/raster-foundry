package com.rasterfoundry.api.campaign

import com.rasterfoundry.akkautil._
import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import cats.effect.IO
import cats.implicits._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor

import java.util.UUID

trait CampaignRoutes
    extends CommonHandlers
    with Directives
    with Authentication
    with PaginationDirectives
    with QueryParametersCommon
    with CampaignProjectRoutes
    with CampaignPermissionRoutes {

  val xa: Transactor[IO]

  val campaignRoutes: Route = {
    pathEndOrSingleSlash {
      get {
        listCampaigns
      } ~
        post {
          createCampaign
        }
    } ~
      pathPrefix(JavaUUID) { campaignId =>
        pathEndOrSingleSlash {
          get {
            getCampaign(campaignId)
          } ~
            put {
              updateCampaign(campaignId)
            } ~
            delete {
              deleteCampaign(campaignId)
            }
        } ~
          pathPrefix("clone") {
            pathEndOrSingleSlash {
              post {
                cloneCampaign(campaignId)
              }
            }
          } ~
          pathPrefix("clone-owners") {
            pathEndOrSingleSlash {
              get {
                listCampaignCloneOwners(campaignId)
              }
            }
          } ~
          pathPrefix("random-review-task") {
            pathEndOrSingleSlash {
              get {
                getReviewTask(campaignId)
              }
            }
          } ~
          pathPrefix("retrieve-child-labels") {
            pathEndOrSingleSlash {
              post {
                retrieveChildCampaignLabels(campaignId)
              }
            }
          } ~
          pathPrefix("permissions") {
            pathEndOrSingleSlash {
              get {
                listCampaignPermissions(campaignId)
              } ~
                put {
                  replaceCampaignPermissions(campaignId)
                } ~
                post {
                  addCampaignPermission(campaignId)
                } ~
                delete {
                  deleteCampaignPermissions(campaignId)
                }
            }
          } ~
          pathPrefix("projects") {
            pathEndOrSingleSlash {
              get {
                listCampaignProjects(campaignId)
              }
            } ~ pathPrefix(JavaUUID) { projectId =>
              pathEndOrSingleSlash {
                getCampaignProject(campaignId, projectId)
              }
            }
          } ~ pathPrefix("actions") {
          pathEndOrSingleSlash {
            get {
              listCampaignUserActions(campaignId)
            }
          }
        } ~ pathPrefix("tasks") {
          pathEndOrSingleSlash {
            get {
              listCampaignTasks(campaignId)
            }
          }
        } ~ pathPrefix("share") {
          pathEndOrSingleSlash {
            get {
              listCampaignShares(campaignId)
            } ~ post {
              shareCampaign(campaignId)
            }
          } ~ pathPrefix(Segment) { deleteId =>
            pathEndOrSingleSlash {
              delete {
                deleteCampaignShare(campaignId, deleteId)
              }
            }
          }
        }
      }
  }

  def listCampaigns: Route = authenticate { user =>
    authorizeScope(
      ScopedAction(Domain.Campaigns, Action.Read, None),
      user
    ) {
      (withPagination & campaignQueryParameters) { (page, campaignQP) =>
        complete {
          CampaignDao
            .listCampaigns(page, campaignQP, user)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def createCampaign: Route = authenticate { user =>
    authorizeScopeLimit(
      CampaignDao.countUserCampaigns(user).transact(xa).unsafeToFuture,
      Domain.Campaigns,
      Action.Create,
      user
    ) {
      entity(as[Campaign.Create]) { newCampaign =>
        onSuccess(
          CampaignDao
            .insertCampaign(newCampaign, user)
            .transact(xa)
            .unsafeToFuture
        ) { campaign =>
          complete((StatusCodes.Created, campaign))
        }
      }
    }
  }

  def getCampaign(campaignId: UUID): Route = authenticate { user =>
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
            ActionType.View
          )
          .transact(xa)
          .unsafeToFuture
      } {
        rejectEmptyResponse {
          complete {
            CampaignDao
              .getCampaignById(campaignId)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }
  }

  def updateCampaign(campaignId: UUID): Route = authenticate { user =>
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
        entity(as[Campaign]) { updatedCampaign =>
          onSuccess(
            CampaignDao
              .updateCampaign(
                updatedCampaign,
                campaignId
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

  def deleteCampaign(campaignId: UUID): Route = authenticate { user =>
    authorizeScope(
      ScopedAction(Domain.Campaigns, Action.Delete, None),
      user
    ) {
      authorizeAuthResultAsync {
        CampaignDao
          .authorized(
            user,
            ObjectType.Campaign,
            campaignId,
            ActionType.Delete
          )
          .transact(xa)
          .unsafeToFuture
      } {
        onSuccess(
          CampaignDao
            .deleteCampaign(campaignId, user)
            .transact(xa)
            .unsafeToFuture
        ) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def cloneCampaign(campaignId: UUID): Route = authenticate { user =>
    authorizeScope(
      ScopedAction(Domain.Campaigns, Action.Clone, None),
      user
    ) {
      authorizeAsync {
        (
          CampaignDao
            .authorized(
              user,
              ObjectType.Campaign,
              campaignId,
              ActionType.View
            ) map {
            _.toBoolean
          },
          CampaignDao.isActiveCampaign(campaignId)
        ).tupled
          .map({ authTup =>
            authTup._1 && authTup._2
          })
          .transact(xa)
          .unsafeToFuture
      } {
        entity(as[Campaign.Clone]) { campaignClone =>
          onSuccess(
            (campaignClone.grantAccessToParentCampaignOwner match {
              case false =>
                CampaignDao.copyCampaign(
                  campaignId,
                  user,
                  Some(campaignClone.tags),
                  campaignClone.copyResourceLink
                )
              case true =>
                for {
                  copiedCampaign <- CampaignDao.copyCampaign(
                    campaignId,
                    user,
                    Some(campaignClone.tags),
                    campaignClone.copyResourceLink
                  )
                  copiedProjects <- AnnotationProjectDao.listByCampaign(
                    copiedCampaign.id
                  )
                  originalCampaignO <- CampaignDao.getCampaignById(
                    campaignId
                  )
                  originalCampaignOwnerO = originalCampaignO map {
                    _.owner
                  }
                  _ <- CampaignDao.addPermission(
                    copiedCampaign.id,
                    ObjectAccessControlRule(
                      SubjectType.User,
                      originalCampaignOwnerO,
                      ActionType.View
                    )
                  )
                  _ <- copiedProjects traverse { project =>
                    AnnotationProjectDao.addPermissionsMany(
                      project.id,
                      List(
                        ObjectAccessControlRule(
                          SubjectType.User,
                          originalCampaignOwnerO,
                          ActionType.View
                        ),
                        ObjectAccessControlRule(
                          SubjectType.User,
                          originalCampaignOwnerO,
                          ActionType.Annotate
                        )
                      )
                    )
                  }
                } yield copiedCampaign
            }).transact(xa).unsafeToFuture
          ) { campaign =>
            complete((StatusCodes.Created, campaign))
          }
        }
      }
    }
  }

  def listCampaignUserActions(campaignId: UUID): Route = authenticate { user =>
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
            ActionType.View
          )
          .transact(xa)
          .unsafeToFuture
      } {
        user.isSuperuser match {
          case true => complete(List("*"))
          case false =>
            onSuccess(
              CampaignDao
                .getCampaignById(campaignId)
                .transact(xa)
                .unsafeToFuture
            ) { campaignO =>
              complete {
                (campaignO traverse { campaign =>
                  campaign.owner == user.id match {
                    case true => List("*").pure[ConnectionIO]
                    case false =>
                      CampaignDao
                        .listUserActions(user, campaignId)
                  }
                } map { _.getOrElse(List[String]()) })
                  .transact(xa)
                  .unsafeToFuture()
              }
            }
        }
      }
    }
  }

  def listCampaignCloneOwners(campaignId: UUID): Route = authenticate { user =>
    authorizeScope(
      ScopedAction(Domain.Campaigns, Action.Clone, None),
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
            .getCloneOwners(campaignId)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def getReviewTask(campaignId: UUID): Route = authenticate { user =>
    authorizeScope(
      ScopedAction(Domain.Campaigns, Action.Read, None),
      user
    ) {
      authorizeAsync {
        CampaignDao
          .isActiveCampaign(campaignId)
          .transact(xa)
          .unsafeToFuture
      } {
        complete {
          CampaignDao
            .randomReviewTask(
              campaignId,
              user
            )
            .transact(xa)
            .unsafeToFuture
        }
      }

    }
  }

  def retrieveChildCampaignLabels(campaignId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Campaigns, Action.Clone, None),
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
          onSuccess {
            CampaignDao
              .retrieveChildCampaignAnnotations(campaignId)
              .transact(xa)
              .unsafeToFuture
          } { complete(StatusCodes.NoContent) }
        }
      }
    }

  def listCampaignTasks(campaignId: UUID): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.Campaigns, Action.Read, None),
        user
      ) {
        authorizeAuthResultAsync(
          CampaignDao
            .authorized(
              user,
              ObjectType.Campaign,
              campaignId,
              ActionType.View
            )
            .transact(xa)
            .unsafeToFuture
        ) {
          (withPagination & taskQueryParameters) { (page, taskParams) =>
            complete {
              (
                TaskDao
                  .listCampaignTasks(
                    taskParams,
                    campaignId,
                    page
                  )
                )
                .transact(xa)
                .unsafeToFuture
            }
          }
        }
      }
    }
}
