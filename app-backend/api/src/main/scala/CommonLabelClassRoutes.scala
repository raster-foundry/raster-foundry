package com.rasterfoundry.api

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

import scala.concurrent.ExecutionContext

import java.util.UUID

class CommonLabelClassRoutes(xa: Transactor[IO], val ec: ExecutionContext)
    extends Directives
    with CommonHandlers {
  def listLabelClasses(labelClassGroupId: UUID): Route =
    complete {
      AnnotationLabelClassDao
        .listAnnotationLabelClassByGroupId(labelClassGroupId)
        .transact(xa)
        .unsafeToFuture
    }

  def addLabelClass(
      labelClassCreate: AnnotationLabelClass.Create,
      labelClassGroupId: UUID
  ): Route =
    complete {
      (
        StatusCodes.Created,
        (for {
          annotationLabelGroupOpt <- AnnotationLabelClassGroupDao
            .getGroupWithClassesById(labelClassGroupId)
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

  def getLabelClass(labelClassId: UUID): Route =
    complete {
      AnnotationLabelClassDao
        .getById(labelClassId)
        .transact(xa)
        .unsafeToFuture
    }

  def updateLabelClass(
      updated: AnnotationLabelClass,
      labelClassId: UUID
  ): Route =
    onSuccess(
      AnnotationLabelClassDao
        .update(
          labelClassId,
          updated
        )
        .transact(xa)
        .unsafeToFuture
    ) {
      completeSingleOrNotFound
    }

  def activeLabelClass(labelClassId: UUID): Route =
    complete {
      AnnotationLabelClassDao
        .activate(labelClassId)
        .transact(xa)
        .unsafeToFuture
    }

  def deactivateLabelClass(labelClassId: UUID): Route =
    onSuccess(
      AnnotationLabelClassDao
        .deactivate(labelClassId)
        .transact(xa)
        .unsafeToFuture
    ) {
      completeSingleOrNotFound
    }

}
