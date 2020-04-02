package com.rasterfoundry.api.annotationProject

import com.rasterfoundry.akkautil._
import com.rasterfoundry.api.user.Auth0Service
import com.rasterfoundry.api.utils.{Config, ManagementBearerToken}
import com.rasterfoundry.database._
import com.rasterfoundry.database.notification.Notify
import com.rasterfoundry.datamodel._
import com.rasterfoundry.notification.intercom.Model._
import com.rasterfoundry.notification.intercom._

import akka.http.scaladsl.server._
import cats.data.OptionT
import cats.effect.IO
import cats.implicits._
import com.softwaremill.sttp.asynchttpclient.cats.AsyncHttpClientCatsBackend
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor

import scala.concurrent.Future

import java.util.UUID

trait AnnotationProjectPermissionRoutes
    extends CommonHandlers
    with Directives
    with Authentication
    with Config {

  val xa: Transactor[IO]

  implicit val sttpBackend = AsyncHttpClientCatsBackend[IO]()
  private val intercomNotifier = new LiveIntercomNotifier[IO]

  private def getSharer(sharingUser: User): String =
    if (sharingUser.email != "") {
      sharingUser.email
    } else if (sharingUser.personalInfo.email != "") {
      sharingUser.personalInfo.email
    } else {
      sharingUser.name
    }

  private def shareNotify(
      sharedUser: User,
      sharingUser: User,
      annotationProjectId: UUID
  ): IO[Either[Throwable, Unit]] =
    intercomNotifier
      .notifyUser(
        intercomToken,
        intercomAdminId,
        ExternalId(sharedUser.id),
        Message(s"""
        | ${getSharer(sharingUser)} has shared a project with you!
        | ${groundworkUrlBase}/app/projects/${annotationProjectId}/overview
        | """.trim.stripMargin)
      )
      .attempt

  private def shareNotifyNewUser(
      bearerToken: ManagementBearerToken,
      sharingUser: User,
      newUserEmail: String,
      newUserId: String,
      sharingUserPlatform: Platform,
      annotationProject: AnnotationProject
  ): Future[Unit] = {
    val subject =
      s"""You've been invited to join the "${annotationProject.name}" project on GroundWork!"""
    (for {
      ticket <- IO.fromFuture {
        IO {
          Auth0Service.createPasswordChangeTicket(
            bearerToken,
            s"$groundworkUrlBase/app/login",
            newUserId
          )
        }
      }
      (messageRich, messagePlain) = Notifications.getInvitationMessage(
        getSharer(sharingUser),
        annotationProject,
        ticket
      )
      _ <- Notify
        .sendEmail(
          sharingUserPlatform.publicSettings,
          sharingUserPlatform.privateSettings,
          newUserEmail,
          subject,
          messageRich.underlying,
          messagePlain.underlying
        )
    } yield ()).attempt.void.unsafeToFuture
  }

  def getDefaultShare(user: User): List[ObjectAccessControlRule] =
    List(
      ObjectAccessControlRule(
        SubjectType.User,
        Some(user.id),
        ActionType.View
      ),
      ObjectAccessControlRule(
        SubjectType.User,
        Some(user.id),
        ActionType.Annotate
      ),
      ObjectAccessControlRule(
        SubjectType.User,
        Some(user.id),
        ActionType.Export
      )
    )

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
              .transact(xa) <* (distinctUserIds traverse { userId =>
              // it's safe to do this unsafely because we know the user exists from
              // the isValidPermission check
              UserDao.unsafeGetUserById(userId).transact(xa) flatMap {
                sharedUser =>
                  shareNotify(sharedUser, user, projectId)
              }
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
              .transact(xa) <*
              (acr.getUserId traverse { userId =>
                UserDao.unsafeGetUserById(userId).transact(xa) flatMap {
                  sharedUser =>
                    shareNotify(sharedUser, user, projectId)
                }
              })).unsafeToFuture
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
      entity(as[UserEmail]) { userByEmail =>
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
                    newUser <- (auth0User.user_id traverse { userId =>
                      UserDao.create(
                        User.Create(
                          userId,
                          email = userByEmail.email,
                          scope = Scopes.GroundworkUser
                        )
                      )
                    }).transact(xa).unsafeToFuture
                    acrs = newUser map { getDefaultShare(_) } getOrElse Nil
                    _ <- (newUser, annotationProjectO).tupled traverse {
                      case (newUser, annotationProject) =>
                        shareNotifyNewUser(
                          managementToken,
                          user,
                          userByEmail.email,
                          newUser.id,
                          userPlatform,
                          annotationProject
                        )
                    }
                    dbAcrs <- (acrs traverse { acr =>
                      AnnotationProjectDao
                        .addPermission(projectId, acr)
                    }).transact(xa).unsafeToFuture
                  } yield dbAcrs
                case existingUsers =>
                  existingUsers traverse { existingUser =>
                    val acrs = getDefaultShare(existingUser)
                    Auth0Service
                      .addGroundworkMetadata(existingUser, managementToken) *>
                      ((acrs traverse { acr =>
                        AnnotationProjectDao
                          .addPermission(projectId, acr)
                      }).transact(xa) <* shareNotify(
                        existingUser,
                        user,
                        projectId
                      )).unsafeToFuture
                  } map { _.flatten }
              }
            } yield permissions)
          }
        }
      }
    }
  }
}
