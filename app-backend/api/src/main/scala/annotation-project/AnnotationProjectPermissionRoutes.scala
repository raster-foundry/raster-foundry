package com.rasterfoundry.api.annotationProject

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
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend

trait AnnotationProjectPermissionRoutes
    extends CommonHandlers
    with Directives
    with Authentication
    with Config {

  val xa: Transactor[IO]

  implicit def contextShift: ContextShift[IO]

  val notifierIOAnnotationProject = AsyncHttpClientCatsBackend[IO]() map {
    backend =>
      new IntercomNotifications(backend)
  }

  def listPermissions(projectId: UUID): Route = authenticate { user =>
    authorizeScope(
      ScopedAction(Domain.AnnotationProjects, Action.ReadPermissions, None),
      user
    ) {
      authorizeAuthResultAsync {
        AnnotationProjectDao
          .authorized(
            user,
            ObjectType.AnnotationProject,
            projectId,
            ActionType.Edit
          )
          .transact(xa)
          .unsafeToFuture
      } {
        complete {
          AnnotationProjectDao
            .getPermissions(projectId)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def replacePermissions(projectId: UUID): Route = authenticate { user =>
    authorizeScope(
      ScopedAction(Domain.AnnotationProjects, Action.Share, None),
      user
    ) {
      entity(as[List[ObjectAccessControlRule]]) { acrList =>
        authorizeAsync {
          (for {
            auth1 <- AnnotationProjectDao.authorized(
              user,
              ObjectType.AnnotationProject,
              projectId,
              ActionType.Edit
            )
            auth2 <- acrList traverse { acr =>
              AnnotationProjectDao.isValidPermission(acr, user)
            }
          } yield {
            auth1.toBoolean && (auth2.foldLeft(true)(_ && _) match {
              case true =>
                AnnotationProjectDao.isReplaceWithinScopedLimit(
                  Domain.AnnotationProjects,
                  user,
                  acrList
                )
              case _ => false
            })
          }).transact(xa).unsafeToFuture()
        } {
          val distinctUserIds = acrList
            .foldMap(acr => acr.getUserId map { Set(_) })
            .getOrElse(Set.empty)
            .toList
          complete {
            (AnnotationProjectDao
              .replacePermissions(projectId, acrList)
              .transact(xa) <* (AnnotationProjectDao
              .unsafeGetById(
                projectId
              )
              .transact(xa) flatMap { annotationProject =>
              (distinctUserIds traverse { userId =>
                // it's safe to do this unsafely because we know the user exists from
                // the isValidPermission check
                UserDao.unsafeGetUserById(userId).transact(xa) flatMap {
                  sharedUser =>
                    notifierIOAnnotationProject flatMap { notifier =>
                      notifier.shareNotify(
                        sharedUser,
                        user,
                        annotationProject,
                        "project"
                      )
                    }
                }
              })
            })).unsafeToFuture
          }
        }
      }
    }
  }

  def addPermission(projectId: UUID): Route = authenticate { user =>
    val shareCount =
      AnnotationProjectDao
        .getShareCount(projectId, user.id)
        .transact(xa)
        .unsafeToFuture
    authorizeScopeLimit(
      shareCount,
      Domain.AnnotationProjects,
      Action.Share,
      user
    ) {
      entity(as[ObjectAccessControlRule]) { acr =>
        authorizeAsync {
          (for {
            auth1 <- AnnotationProjectDao.authorized(
              user,
              ObjectType.AnnotationProject,
              projectId,
              ActionType.Edit
            )
            auth2 <- AnnotationProjectDao.isValidPermission(acr, user)
          } yield {
            auth1.toBoolean && auth2
          }).transact(xa).unsafeToFuture()
        } {
          complete {
            (AnnotationProjectDao
              .addPermission(projectId, acr)
              .transact(xa) <* (
              AnnotationProjectDao
                .unsafeGetById(projectId)
                .transact(xa) flatMap { annotationProject =>
                acr.getUserId traverse { userId =>
                  UserDao.unsafeGetUserById(userId).transact(xa) flatMap {
                    sharedUser =>
                      notifierIOAnnotationProject flatMap { notifier =>
                        notifier.shareNotify(
                          sharedUser,
                          user,
                          annotationProject,
                          "project"
                        )
                      }
                  }
                }
              }
            )).unsafeToFuture
          }
        }
      }
    }
  }

  def deletePermissions(projectId: UUID): Route = authenticate { user =>
    authorizeScope(
      ScopedAction(Domain.AnnotationProjects, Action.Share, None),
      user
    ) {
      authorizeAuthResultAsync {
        AnnotationProjectDao
          .authorized(
            user,
            ObjectType.AnnotationProject,
            projectId,
            ActionType.Edit
          )
          .transact(xa)
          .unsafeToFuture
      } {
        complete {
          AnnotationProjectDao
            .deletePermissions(projectId)
            .transact(xa)
            .unsafeToFuture
        }
      }
    }
  }

  def listAnnotationProjectShares(projectId: UUID): Route = authenticate {
    user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.Share, None),
        user
      ) {
        authorizeAuthResultAsync {
          AnnotationProjectDao
            .authorized(
              user,
              ObjectType.AnnotationProject,
              projectId,
              ActionType.Edit
            )
            .transact(xa)
            .unsafeToFuture
        } {
          complete {
            AnnotationProjectDao
              .getSharedUsers(projectId)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
  }

  def deleteAnnotationProjectShare(projectId: UUID, deleteId: String): Route =
    authenticate { user =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.Read, None),
        user
      ) {
        (if (user.id == deleteId) {
           authorizeAuthResultAsync {
             AnnotationProjectDao
               .authorized(
                 user,
                 ObjectType.AnnotationProject,
                 projectId,
                 ActionType.View
               )
               .transact(xa)
               .unsafeToFuture
           }
         } else {
           authorizeAuthResultAsync {
             AnnotationProjectDao
               .authorized(
                 user,
                 ObjectType.AnnotationProject,
                 projectId,
                 ActionType.Edit
               )
               .transact(xa)
               .unsafeToFuture
           }
         }) {
          complete {
            AnnotationProjectDao
              .deleteSharedUser(projectId, deleteId)
              .map(c => if (c > 0) 1 else 0)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }

  def shareAnnotationProject(projectId: UUID): Route = authenticate { user =>
    val shareCount =
      AnnotationProjectDao
        .getShareCount(projectId, user.id)
        .transact(xa)
        .unsafeToFuture
    authorizeScopeLimit(
      shareCount,
      Domain.AnnotationProjects,
      Action.Share,
      user
    ) {
      entity(as[UserShareInfo]) { userByEmail =>
        authorize {
          (userByEmail.actionType match {
            case Some(ActionType.Annotate) | Some(ActionType.Validate) | None =>
              true
            case _ => false
          })
        } {
          complete {
            Auth0Service.getManagementBearerToken flatMap { managementToken =>
              (for {
                // Everything has to be Futures here because of methods in akka-http / Auth0Service
                users <- UserDao
                  .findUsersByEmail(userByEmail.email)
                  .transact(xa)
                  .unsafeToFuture
                userPlatform <- UserDao
                  .unsafeGetUserPlatform(user.id)
                  .transact(xa)
                  .unsafeToFuture
                annotationProjectO <- AnnotationProjectDao
                  .getById(projectId)
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
                      notifier <- notifierIOAnnotationProject.unsafeToFuture
                      acrs = newUserOpt map { newUser =>
                        notifier
                          .getDefaultShare(newUser, userByEmail.actionType)
                      } getOrElse Nil
                      _ <- (newUserOpt, annotationProjectO).tupled traverse {
                        case (newUser, annotationProject) =>
                          // if silent param is not provided, we notify
                          val isSilent = userByEmail.silent.getOrElse(false)
                          if (!isSilent) {
                            notifier.shareNotifyNewUser(
                              managementToken,
                              user,
                              userByEmail.email,
                              newUser.id,
                              userPlatform,
                              annotationProject,
                              "project",
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
                        AnnotationProjectDao
                          .addPermission(projectId, acr)
                      }).transact(xa).unsafeToFuture
                    } yield dbAcrs
                  case existingUsers =>
                    existingUsers traverse { existingUser =>
                      notifierIOAnnotationProject.unsafeToFuture flatMap {
                        notifier =>
                          val acrs =
                            notifier.getDefaultShare(
                              existingUser,
                              userByEmail.actionType
                            )
                          Auth0Service
                            .addUserMetadata(
                              existingUser.id,
                              managementToken,
                              Map("app_metadata" -> Map("annotateApp" -> true)).asJson
                            ) *>
                            (AnnotationProjectDao
                              .handleSharedPermissions(
                                projectId,
                                existingUser.id,
                                acrs,
                                userByEmail.actionType
                              )
                              .transact(xa) <* (
                              // if silent param is not provided, we notify
                              userByEmail.silent match {
                                case Some(false) | None =>
                                  AnnotationProjectDao
                                    .unsafeGetById(projectId)
                                    .transact(xa) flatMap { annotationProject =>
                                    notifier.shareNotify(
                                      existingUser,
                                      user,
                                      annotationProject,
                                      "project"
                                    )
                                  }
                                case _ => IO.pure(())
                              }
                            )).unsafeToFuture
                      }

                    } map { _.flatten }
                }
              } yield permissions)
            }
          }
        }
      }
    }
  }
}
