package com.rasterfoundry.api.annotationProject

import com.rasterfoundry.akkautil._
import com.rasterfoundry.api.CommonLabelClassGroupRoutes
import com.rasterfoundry.api.utils.queryparams.QueryParametersCommon
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import cats.effect._
import cats.implicits._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor

import scala.util.{Failure, Success}

import java.util.UUID

trait AnnotationProjectLabelClassGroupRoutes
    extends CommonHandlers
    with Directives
    with Authentication
    with PaginationDirectives
    with QueryParametersCommon {

  val xa: Transactor[IO]

  def commonLabelClassGroupRoutes: CommonLabelClassGroupRoutes

  def listAnnotationProjectLabelClassGroups(projectId: UUID): Route =
    authenticate { case MembershipAndUser(_, user) =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.Read, None),
        user
      ) {
        authorizeAuthResultAsync {
          AnnotationProjectDao
            .authorized(
              user,
              ObjectType.AnnotationProject,
              projectId,
              ActionType.Annotate
            )
            .transact(xa)
            .unsafeToFuture
        } {
          complete {
            (
              AnnotationLabelClassGroupDao
                .listByProjectIdWithClasses(projectId)
              )
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }

  def createAnnotationProjectLabelClassGroup(projectId: UUID): Route =
    authenticate { case MembershipAndUser(_, user) =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.Update, None),
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
          entity(as[AnnotationLabelClassGroup.Create]) { classGroupCreate =>
            onComplete {
              (for {
                groups <- AnnotationLabelClassGroupDao.listByProjectId(
                  projectId)
                projectOpt <- AnnotationProjectDao.getById(projectId)
                created <- projectOpt traverse { project =>
                  AnnotationLabelClassGroupDao.insertAnnotationLabelClassGroup(
                    classGroupCreate,
                    Some(project),
                    None,
                    groups.size // new class group should be appended to the end
                  )
                }
              } yield created)
                .transact(xa)
                .unsafeToFuture
            } {
              case Success(Some(groupsWithClasses)) =>
                complete { groupsWithClasses }
              case Success(None) =>
                complete {
                  StatusCodes.NotFound -> "Annotation project does not exist"
                }
              case Failure(e) =>
                logger.error(e.getMessage)
                complete { HttpResponse(StatusCodes.BadRequest) }
            }
          }
        }
      }
    }

  def getAnnotationProjectLabelClassGroup(
      projectId: UUID,
      classGroupId: UUID
  ): Route =
    authenticate { case MembershipAndUser(_, user) =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.Read, None),
        user
      ) {
        {
          authorizeAuthResultAsync {
            AnnotationProjectDao
              .authorized(
                user,
                ObjectType.AnnotationProject,
                projectId,
                ActionType.Annotate
              )
              .transact(xa)
              .unsafeToFuture
          } {
            commonLabelClassGroupRoutes.getLabelClassGroup(classGroupId)
          }
        }
      }
    }

  def updateAnnotationProjectLabelClassGroup(
      projectId: UUID,
      classGroupId: UUID
  ): Route =
    authenticate { case MembershipAndUser(_, user) =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.Update, None),
        user
      ) {
        {
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
            entity(as[AnnotationLabelClassGroup]) { updatedClassGroup =>
              commonLabelClassGroupRoutes.updateLabelClassGroup(
                updatedClassGroup,
                classGroupId
              )
            }
          }
        }
      }
    }

  def activateAnnotationProjectLabelClassGroup(
      projectId: UUID,
      classGroupId: UUID
  ): Route =
    authenticate { case MembershipAndUser(_, user) =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.Update, None),
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
          commonLabelClassGroupRoutes.activateLabelClassGroup(classGroupId)
        }

      }
    }

  def deactivateAnnotationProjectLabelClassGroup(
      projectId: UUID,
      classGroupId: UUID
  ): Route =
    authenticate { case MembershipAndUser(_, user) =>
      authorizeScope(
        ScopedAction(Domain.AnnotationProjects, Action.Update, None),
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
          commonLabelClassGroupRoutes.deactivateLabelClassGroup(classGroupId)
        }
      }
    }
}
