package com.rasterfoundry.api.annotationProject

import com.rasterfoundry.akkautil._
import com.rasterfoundry.api.CommonLabelClassRoutes
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.server._
import cats.effect._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._

import java.util.UUID

trait AnnotationProjectLabelClassRoutes
    extends CommonHandlers
    with Directives
    with Authentication {

  val xa: Transactor[IO]

  def commonLabelClassRoutes: CommonLabelClassRoutes

  def listAnnotationProjectGroupLabelClasses(
      projectId: UUID,
      labelClassGroupId: UUID
  ): Route =
    authenticate { case (user, _) =>
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
          commonLabelClassRoutes.listLabelClasses(labelClassGroupId)
        }
      }
    }

  def addAnnotationProjectLabelClassToGroup(
      projectId: UUID,
      groupId: UUID
  ): Route =
    authenticate { case (user, _) =>
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
            commonLabelClassRoutes.addLabelClass(labelClassCreate, groupId)
          }
        }
      }
    }

  def getAnnotationProjectLabelClass(
      projectId: UUID,
      labelClassId: UUID
  ): Route =
    authenticate { case (user, _) =>
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
            commonLabelClassRoutes.getLabelClass(
              labelClassId
            )
          }
        }
      }
    }

  def updateAnnotationProjectLabelClass(
      projectId: UUID,
      labelClassId: UUID
  ): Route =
    authenticate { case (user, _) =>
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
              commonLabelClassRoutes.updateLabelClass(
                updatedClass,
                labelClassId
              )
            }
          }
        }
      }
    }

  def activateAnnotationProjectLabelClass(
      projectId: UUID,
      labelClassId: UUID
  ): Route =
    authenticate { case (user, _) =>
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
          commonLabelClassRoutes.activeLabelClass(labelClassId)
        }

      }
    }

  def deactivateAnnotationProjectLabelClass(
      projectId: UUID,
      labelClassId: UUID
  ): Route =
    authenticate { case (user, _) =>
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
          commonLabelClassRoutes.deactivateLabelClass(labelClassId)
        }
      }
    }
}
