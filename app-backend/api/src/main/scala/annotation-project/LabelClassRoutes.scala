package com.rasterfoundry.api.annotationProject

import com.rasterfoundry.akkautil._
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

trait LabelClassRoutes
    extends CommonHandlers
    with Directives
    with Authentication {

  val xa: Transactor[IO]

  def listGroupLabelClasses(projectId: UUID, labelClassGroupId: UUID): Route =
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
            AnnotationLabelClassDao
              .listAnnotationLabelClassByGroupId(labelClassGroupId)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }

  def addLabelClassToGroup(projectId: UUID, groupId: UUID): Route =
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
          entity(as[AnnotationLabelClass.Create]) { labelClassCreate =>
            complete(
              StatusCodes.Created,
              (for {
                annotationLabelGroupOpt <-
                  AnnotationLabelClassGroupDao.getGroupWithClassesById(groupId)
                insert <- annotationLabelGroupOpt traverse { groupWithClass =>
                  AnnotationLabelClassDao
                    .insertAnnotationLabelClass(
                      labelClassCreate,
                      groupWithClass.toClassGroup,
                      None
                    )
                }

              } yield insert)
                .transact(xa)
                .unsafeToFuture
            )
          }
        }
      }
    }

  def getLabelClass(projectId: UUID, labelClassId: UUID): Route =
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
              AnnotationLabelClassDao
                .getById(labelClassId)
                .transact(xa)
                .unsafeToFuture
            }
          }
        }
      }
    }

  def updateLabelClass(projectId: UUID, labelClassId: UUID): Route =
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
            entity(as[AnnotationLabelClass]) { updatedClass =>
              onSuccess(
                AnnotationLabelClassDao
                  .update(
                    labelClassId,
                    updatedClass
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

  def softDeleteLabelClass(projectId: UUID, labelClassId: UUID): Route =
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
            AnnotationLabelClassDao
              .deactivate(labelClassId)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }

  def activateLabelClassGroup(projectId: UUID, labelClassId: UUID): Route =
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
              .activate(labelClassId)
              .transact(xa)
              .unsafeToFuture
          }
        }

      }
    }

  def deactivateLabelClassGroup(
      projectId: UUID,
      labelClassId: UUID
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
          complete {
            AnnotationLabelClassDao
              .deactivate(labelClassId)
              .transact(xa)
              .unsafeToFuture
          }
        }
      }
    }
}
