package com.rasterfoundry.api.annotationProject

import com.rasterfoundry.akkautil._
import com.rasterfoundry.api.user.Auth0Service
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

trait AnnotationProjectPermissionRoutes
    extends CommonHandlers
    with Directives
    with Authentication {

  val xa: Transactor[IO]

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
            auth1.toBoolean && auth2.foldLeft(true)(_ && _)
          }).transact(xa).unsafeToFuture()
        } {
          complete {
            AnnotationProjectDao
              .replacePermissions(projectId, acrList)
              .transact(xa)
              .unsafeToFuture
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
            AnnotationProjectDao
              .addPermission(projectId, acr)
              .transact(xa)
              .unsafeToFuture
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
                    auth0User <- Auth0Service
                      .createGroundworkUser(userByEmail.email, managementToken)
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
                case us =>
                  val acrs = getDefaultShare(user)
                  us traverse { user =>
                    Auth0Service.addGroundworkMetadata(user, managementToken) *>
                      (acrs traverse { acr =>
                        AnnotationProjectDao
                          .addPermission(projectId, acr)
                      }).transact(xa).unsafeToFuture
                  } map { _.flatten }
              }
            } yield permissions)
          }
        }
      }
    }
  }
}
