package com.rasterfoundry.api.annotationProject

import com.rasterfoundry.akkautil._
import com.rasterfoundry.api.user.Auth0Service
import com.rasterfoundry.api.utils.{
  Config,
  IntercomNotifications,
  ManagementBearerToken
}
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import cats.data.{OptionT, Validated, ValidatedNel}
import cats.effect.{ContextShift, IO}
import cats.implicits._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.circe.syntax._

import java.util.UUID

trait AnnotationProjectPermissionRoutes
    extends CommonHandlers
    with Directives
    with Authentication
    with Config {

  val xa: Transactor[IO]

  implicit def contextShift: ContextShift[IO]

  def notifier: IntercomNotifications

  private def shareWithExistingUsers(
      users: List[User],
      userByEmail: UserShareInfo,
      managementToken: ManagementBearerToken,
      projectId: UUID,
      sharingUser: User
  ): IO[ValidatedNel[String, List[ObjectAccessControlRule]]] =
    users traverse { existingUser =>
      (
        for {
          auth0User <- IO.fromFuture {
            IO {
              Auth0Service
                .addUserMetadata(
                  existingUser.id,
                  managementToken,
                  Map(
                    "app_metadata" -> Map("annotateApp" -> true)
                  ).asJson
                )
            }
          }
          () <- IO { logger.debug(s"Got auth0 user: $auth0User") }
          acrs = notifier.getDefaultShare(
            existingUser,
            userByEmail.actionType
          )
          dbAcrs <-
            AnnotationProjectDao
              .handleSharedPermissions(
                projectId,
                existingUser.id,
                acrs,
                userByEmail.actionType
              )
              .transact(xa)
          // if silent param is not provided, we notify
          _ <- userByEmail.silent match {
            case Some(false) | None =>
              AnnotationProjectDao
                .unsafeGetById(projectId)
                .transact(xa) flatMap { annotationProject =>
                notifier.shareNotify(
                  existingUser,
                  sharingUser,
                  annotationProject,
                  "projects"
                )
              }
            case _ => IO.pure(())
          }
        } yield dbAcrs
      ).attempt map {
        case Left(_)     => userByEmail.email.invalidNel
        case Right(acrs) => acrs.validNel
      }
    } map { _.combineAll }

  def listPermissions(projectId: UUID): Route =
    authenticate {
      case (user, _) =>
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

  def replacePermissions(projectId: UUID): Route =
    authenticate {
      case (user, _) =>
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
                        notifier.shareNotify(
                          sharedUser,
                          user,
                          annotationProject,
                          "projects"
                        )
                    }
                  })
                })).unsafeToFuture
              }
            }
          }
        }
    }

  def addPermission(projectId: UUID): Route =
    authenticate {
      case (user, _) =>
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
                          notifier.shareNotify(
                            sharedUser,
                            user,
                            annotationProject,
                            "projects"
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

  def deletePermissions(projectId: UUID): Route =
    authenticate {
      case (user, _) =>
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

  def listAnnotationProjectShares(projectId: UUID): Route =
    authenticate {
      case (user, _) =>
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
    authenticate {
      case (user, _) =>
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

  def shareAnnotationProject(projectId: UUID): Route =
    authenticate {
      case (user, _) =>
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
                case Some(ActionType.Annotate) | Some(ActionType.Validate) |
                    None =>
                  true
                case _ => false
              })
            } {
              onSuccess {
                Auth0Service.getManagementBearerToken flatMap {
                  managementToken =>
                    (for {
                      // Everything has to be Futures here because of methods in akka-http / Auth0Service
                      users <-
                        UserDao
                          .findUsersByEmail(userByEmail.email)
                          .transact(xa)
                      userPlatform <-
                        UserDao
                          .unsafeGetUserPlatform(user.id)
                          .transact(xa)
                      annotationProjectO <-
                        AnnotationProjectDao
                          .getById(projectId)
                          .transact(xa)
                      permissions <- users match {
                        case Nil =>
                          for {
                            auth0User <- OptionT {
                              IO.fromFuture {
                                IO {
                                  Auth0Service.findGroundworkUser(
                                    userByEmail.email,
                                    managementToken
                                  )
                                }
                              }
                            } getOrElseF {
                              IO.fromFuture {
                                IO {
                                  Auth0Service
                                    .createGroundworkUser(
                                      userByEmail.email,
                                      managementToken
                                    )
                                }
                              }
                            }
                            newUserOpt <-
                              (auth0User.user_id traverse { userId =>
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
                              }).transact(xa)
                            acrs = newUserOpt map { newUser =>
                              notifier
                                .getDefaultShare(
                                  newUser,
                                  userByEmail.actionType
                                )
                            } getOrElse Nil
                            _ <-
                              (newUserOpt, annotationProjectO).tupled traverse {
                                case (newUser, annotationProject) =>
                                  // if silent param is not provided, we notify
                                  val isSilent =
                                    userByEmail.silent.getOrElse(false)
                                  if (!isSilent) {
                                    IO.fromFuture {
                                      IO {
                                        notifier.shareNotifyNewUser(
                                          managementToken,
                                          user,
                                          userByEmail.email,
                                          newUser.id,
                                          userPlatform,
                                          annotationProject,
                                          "projects",
                                          Notifications.getInvitationMessage
                                        )
                                      }
                                    }
                                  } else {
                                    IO.unit
                                  }
                              }
                            // this is not an existing user,
                            // there is no project specific ACR yet,
                            // so no need to remove Validate action if only want Annotate
                            dbAcrs <- (acrs flatTraverse { acr =>
                                AnnotationProjectDao
                                  .addPermission(projectId, acr)
                              }).transact(xa)
                          } yield dbAcrs.validNel
                        case existingUsers =>
                          shareWithExistingUsers(
                            existingUsers,
                            userByEmail,
                            managementToken,
                            projectId,
                            user
                          )
                      }
                    } yield permissions).unsafeToFuture
                }
              } {
                case Validated.Invalid(errs) =>
                  complete {
                    (
                      StatusCodes.BadRequest,
                      Map("failedUsers" -> errs).asJson
                    )
                  }
                case Validated.Valid(acrs) =>
                  complete { acrs.asJson }
              }
            }
          }
        }
    }
}
