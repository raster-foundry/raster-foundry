package com.rasterfoundry.api

import com.rasterfoundry.akkautil._
import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._

import akka.http.scaladsl.server._
import cats.effect._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor

import scala.concurrent.ExecutionContext

import java.util.UUID

class CommonLabelClassGroupRoutes(
    xa: Transactor[IO],
    val ec: ExecutionContext
) extends Directives
    with CommonHandlers {
  def getLabelClassGroup(classGroupId: UUID): Route =
    complete {
      AnnotationLabelClassGroupDao
        .getGroupWithClassesById(classGroupId)
        .transact(xa)
        .unsafeToFuture
    }

  def updateLabelClassGroup(
      updated: AnnotationLabelClassGroup,
      labelClassGroupId: UUID
  ): Route =
    onSuccess(
      AnnotationLabelClassGroupDao
        .update(
          labelClassGroupId,
          updated
        )
        .transact(xa)
        .unsafeToFuture
    ) {
      completeSingleOrNotFound
    }

  def activateLabelClassGroup(
      labelClassGroupId: UUID
  ): Route =
    complete {
      AnnotationLabelClassGroupDao
        .activate(labelClassGroupId)
        .transact(xa)
        .unsafeToFuture
    }

  def deactivateLabelClassGroup(
      labelClassGroupId: UUID
  ): Route =
    onSuccess(
      AnnotationLabelClassGroupDao
        .deactivate(labelClassGroupId)
        .transact(xa)
        .unsafeToFuture
    ) {
      completeSingleOrNotFound
    }

}
