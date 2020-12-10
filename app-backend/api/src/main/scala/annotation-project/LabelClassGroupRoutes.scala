package com.rasterfoundry.api.annotationProject

import com.rasterfoundry.akkautil._
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

trait LabelClassGroupRoutes
    extends CommonHandlers
    with Directives
    with Authentication
    with PaginationDirectives
    with QueryParametersCommon {

  val xa: Transactor[IO]

  def listLabelClassGroups(projectId: UUID): Route =
    authenticate { user =>
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

  def createLabelClassGroup(projectId: UUID): Route =
    authenticate { user =>
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

  def getLabelClassGroup(projectId: UUID, classGroupId: UUID): Route =
    authenticate { user =>
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
            complete {
              AnnotationLabelClassGroupDao
                .getGroupWithClassesById(classGroupId)
                .transact(xa)
                .unsafeToFuture
            }
          }
        }
      }
    }

  def updateLabelClassGroup(projectId: UUID, labelClassGroupId: UUID): Route =
    authenticate { user =>
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
              onSuccess(
                AnnotationLabelClassGroupDao
                  .update(
                    labelClassGroupId,
                    updatedClassGroup
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
    }

  def activateLabelClassGroup(projectId: UUID, labelClassGroupId: UUID): Route =
    authenticate { user =>
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
          complete {
            AnnotationLabelClassGroupDao
              .activate(labelClassGroupId)
              .transact(xa)
              .unsafeToFuture
          }
        }

      }
    }

  def deactivateLabelClassGroup(
      projectId: UUID,
      labelClassGroupId: UUID
  ): Route =
    authenticate { user =>
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
          onSuccess(
             AnnotationLabelClassGroupDao
              .deactivate(labelClassGroupId)
              .transact(xa)
              .unsafeToFuture
          ) {
            completeSingleOrNotFound
          }
        }
      }
    }
}
