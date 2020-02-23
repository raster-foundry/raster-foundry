package com.rasterfoundry.api.annotationProject

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

trait AnnotationProjectPermissionRoutes
    extends CommonHandlers
    with Directives
    with Authentication {

  val xa: Transactor[IO]

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
        val io = for {
          users <- UserDao.findUsersByEmail(userByEmail.email)
          // exist branch:
          // - share with all users with this email
          // - update auth0 to make sure those users have annotate access
          permissions <- users match {
            case Nil =>
              ???
            case us =>
              us flatMap { user =>
                List(
                  ObjectAccessControlRule(SubjectType.User, Some(user.id), ActionType.View),
                  ObjectAccessControlRule(SubjectType.User, Some(user.id), ActionType.Annotate),
                  ObjectAccessControlRule(SubjectType.User, Some(user.id), ActionType.Export)
                )
              } traverse { acr =>
                AnnotationProjectDao.addPermission(projectId, acr)
              }
          }
        } yield permissions
        complete {
          io.transact(xa).unsafeToFuture
        }
      }
    }
  }
}
