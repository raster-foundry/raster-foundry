package com.rasterfoundry.api.annotationProject

import com.rasterfoundry.akkautil._
import com.rasterfoundry.api.user.Auth0Service
import com.rasterfoundry.api.utils.Config
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._
import com.rasterfoundry.notification.intercom._
import com.rasterfoundry.notification.intercom.Model._

import akka.http.scaladsl.server._
import cats.data.OptionT
import cats.effect.IO
import cats.implicits._
import com.softwaremill.sttp.asynchttpclient.cats.AsyncHttpClientCatsBackend
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor

import java.util.UUID

trait AnnotationProjectPermissionRoutes
    extends CommonHandlers
    with Directives
    with Authentication
    with Config {

  val xa: Transactor[IO]

  implicit val sttpBackend = AsyncHttpClientCatsBackend[IO]()
  private val intercomNotifier = new LiveIntercomNotifier[IO]

  private def shareNotify(
      user: User,
      annotationProjectId: UUID
  ): IO[Either[Throwable, Unit]] =
    intercomNotifier
      .notifyUser(
        intercomToken,
        intercomAdminId,
        ExternalId(user.id),
        Message(s"""
        | ${user.name} has shared a project with you!
        | ${groundworkUrlBase}/app/projects/${annotationProjectId}/overview
        | """.trim.stripMargin)
      )
      .attempt

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
            .foldLeft(Set.empty[String])(
              (accum: Set[String], acr: ObjectAccessControlRule) =>
                acr.getUserId map { accum | Set(_) } getOrElse accum
            )
            .toList
          complete {
            (AnnotationProjectDao
              .replacePermissions(projectId, acrList)
              .transact(xa) <* (distinctUserIds traverse { userId =>
              // it's safe to do this unsafely because we know the user exists from
              // the isValidPermission check
              UserDao.unsafeGetUserById(userId).transact(xa) flatMap { user =>
                shareNotify(user, projectId)
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
                UserDao.unsafeGetUserById(userId).transact(xa) flatMap { user =>
                  shareNotify(user, projectId)
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
                    user <- (auth0User.user_id traverse { userId =>
                      UserDao.create(
                        User.Create(
                          userId,
                          email = userByEmail.email,
                          scope = Scopes.GroundworkUser
                        )
                      )
                    }).transact(xa).unsafeToFuture
                    acrs = user map { getDefaultShare(_) } getOrElse Nil
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
                      }).transact(xa) <* shareNotify(existingUser, projectId)).unsafeToFuture
                  } map { _.flatten }
              }
            } yield permissions)
          }
        }
      }
    }
  }
}
