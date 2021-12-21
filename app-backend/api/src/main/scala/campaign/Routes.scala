package com.rasterfoundry.api.campaign

import com.rasterfoundry.akkautil._
import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import cats.effect._
import cats.implicits._
import com.google.common.util.concurrent.ThreadFactoryBuilder
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor

import scala.concurrent.ExecutionContext

import java.util.UUID
import java.util.concurrent.Executors

trait CampaignRoutes
    extends CommonHandlers
    with Directives
    with Authentication
    with PaginationDirectives
    with QueryParametersCommon
    with CampaignProjectRoutes
    with CampaignPermissionRoutes
    with CampaignLabelClassGroupRoutes
    with CampaignLabelClassRoutes {

  val xa: Transactor[IO]

  val campaignDeleteContext = ExecutionContext.fromExecutor(
    Executors.newCachedThreadPool(
      new ThreadFactoryBuilder().setNameFormat("campaign-delete-%d").build()
    )
  )
  val campaignDeleteContextShift = IO.contextShift(campaignDeleteContext)
  val campaignDeleteBlocker =
    Blocker.liftExecutionContext(campaignDeleteContext)

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
          pathPrefix("label-class-summary") {
            pathEndOrSingleSlash {
              get {
                getCampaignLabelClassSummary(campaignId)
              }
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
          } ~ pathPrefix("label-class-groups") {
          pathEndOrSingleSlash {
            get {
              listCampaignLabelClassGroups(campaignId)
            } ~ post {
              createCampaignLabelClassGroup(campaignId)
            }
          } ~ pathPrefix(JavaUUID) { labelClassGroupId =>
            pathEndOrSingleSlash {
              get {
                getCampaignLabelClassGroup(
                  campaignId,
                  labelClassGroupId
                )
              } ~ put {
                updateCampaignLabelClassGroup(
                  campaignId,
                  labelClassGroupId
                )
              }
            } ~ pathPrefix("activate") {
              post {
                activateCampaignLabelClassGroup(
                  campaignId,
                  labelClassGroupId
                )
              }
            } ~ pathPrefix("deactivate") {
              delete {
                deactivateCampaignLabelClassGroup(
                  campaignId,
                  labelClassGroupId
                )
              }
            } ~ pathPrefix("label-classes") {
              pathEndOrSingleSlash {
                get {
                  listCampaignGroupLabelClasses(
                    campaignId,
                    labelClassGroupId
                  )
                } ~
                  post {
                    addCampaignLabelClassToGroup(
                      campaignId,
                      labelClassGroupId
                    )
                  }
              } ~ pathPrefix(JavaUUID) { labelClassId =>
                pathEndOrSingleSlash {
                  get {
                    getCampaignLabelClass(campaignId, labelClassId)
                  } ~ put {
                    updateCampaignLabelClass(campaignId, labelClassId)
                  }
                } ~ pathPrefix("activate") {
                  post {
                    activateCampaignLabelClass(campaignId, labelClassId)
                  }
                } ~ pathPrefix("deactivate") {
                  delete {
                    deactivateCampaignLabelClass(campaignId, labelClassId)
                  }
                }
              }
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
        } ~ pathPrefix("performance") {
          pathPrefix("label") {
            pathEndOrSingleSlash {
              get { getLabelingPerformance(campaignId) }
            }
          } ~ pathPrefix("validate") {
            pathEndOrSingleSlash {
              get { getValidatingPerformance(campaignId) }
            }
          }
        }
      }
  }

  def listCampaigns: Route =
    authenticate {
      case (user, _) =>
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

  def createCampaign: Route =
    authenticate {
      case (user, _) =>
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

  def getCampaign(campaignId: UUID): Route =
    authenticate {
      case (user, _) =>
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
                  .getCampaignWithRelatedById(campaignId)
                  .transact(xa)
                  .unsafeToFuture
              }
            }
          }
        }
    }

  def updateCampaign(campaignId: UUID): Route =
    authenticate {
      case (user, _) =>
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

  def deleteCampaign(campaignId: UUID): Route =
    authenticate {
      case (user, _) =>
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
            val updateFieldIO = for {
              campaignOpt <- CampaignDao.getCampaignById(campaignId)
              _ <- campaignOpt traverse { campaign =>
                CampaignDao.updateCampaign(
                  campaign.copy(isActive = false),
                  campaign.id
                )
              }
              projectsOpt <- campaignOpt traverse { campaign =>
                AnnotationProjectDao.query
                  .filter(fr"campaign_id = ${campaign.id}")
                  .list
              }
              updated <- projectsOpt traverse { projects =>
                projects traverse { project =>
                  AnnotationProjectDao
                    .update(project.copy(isActive = false), project.id)
                }
              }
            } yield updated

            val deleteIO = for {
              _ <- updateFieldIO.transact(xa)
              deleted <- campaignDeleteBlocker.blockOn(
                CampaignDao
                  .deleteCampaign(campaignId, user)
                  .transact(xa)
                  .start(campaignDeleteContextShift)
              )(campaignDeleteContextShift)
            } yield deleted

            complete(
              StatusCodes.Accepted,
              deleteIO.void.unsafeToFuture
            )
          }
        }
    }

  def cloneCampaign(campaignId: UUID): Route =
    authenticate {
      case (user, _) =>
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

  def listCampaignUserActions(campaignId: UUID): Route =
    authenticate {
      case (user, _) =>
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

  def listCampaignCloneOwners(campaignId: UUID): Route =
    authenticate {
      case (user, _) =>
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

  def getReviewTask(campaignId: UUID): Route =
    authenticate {
      case (user, _) =>
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
            (campaignRandomTaskQueryParameters) { randomTaskQp =>
              complete {
                (
                  for {
                    randomTaskOpt <- CampaignDao.randomReviewTask(
                      campaignId,
                      user
                    )
                    acrsOpt = randomTaskQp.requestAction.toList.toNel map {
                      actions =>
                        actions map { action =>
                          ObjectAccessControlRule(
                            SubjectType.User,
                            Some(user.id),
                            action
                          )
                        }
                    }
                    _ <- (randomTaskOpt, acrsOpt).tupled traverse {
                      case (randomTask, acrs) =>
                        (
                          CampaignDao.addPermissionsMany(
                            randomTask.properties.campaignId,
                            acrs.toList,
                            false
                          ),
                          AnnotationProjectDao.addPermissionsMany(
                            randomTask.properties.annotationProjectId,
                            acrs.toList,
                            false
                          )
                        ).tupled
                    } void
                  } yield randomTaskOpt
                ).transact(xa).unsafeToFuture
              }
            }
          }

        }
    }

  def retrieveChildCampaignLabels(campaignId: UUID): Route =
    authenticate {
      case (user, _) =>
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
    authenticate {
      case (user, _) =>
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

  def getCampaignLabelClassSummary(campaignId: UUID): Route =
    authenticate {
      case (user, _) =>
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
                  .getLabelClassSummary(campaignId)
                  .transact(xa)
                  .unsafeToFuture
              }
            }
          }
        }
    }

  private def getPerformance(
      campaignId: UUID,
      taskSessionType: TaskSessionType
  ): Route =
    authenticate {
      case (user, _) =>
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
                ActionType.Validate
              )
              .transact(xa)
              .unsafeToFuture
          ) {
            withPagination { page =>
              complete {
                CampaignDao
                  .performance(
                    campaignId,
                    taskSessionType,
                    page
                  )
                  .transact(xa)
                  .unsafeToFuture
              }
            }
          }
        }
    }

  def getValidatingPerformance(campaignId: UUID): Route =
    getPerformance(campaignId, TaskSessionType.ValidateSession)

  def getLabelingPerformance(campaignId: UUID): Route =
    getPerformance(campaignId, TaskSessionType.LabelSession)
}
