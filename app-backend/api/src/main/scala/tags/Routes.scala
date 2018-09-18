package com.azavea.rf.api.tooltag

import com.azavea.rf.authentication.Authentication
import com.azavea.rf.common.{CommonHandlers, UserErrorHandler}
import com.azavea.rf.database.ToolTagDao
import com.azavea.rf.datamodel._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import io.circe._
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._

import scala.util.{Failure, Success}
import java.util.UUID

import cats.effect.IO
import com.azavea.rf.database.filter.Filterables._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._

trait ToolTagRoutes
    extends Authentication
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler {
  val xa: Transactor[IO]

  val toolTagRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listToolTags } ~
        post { createToolTag }
    } ~
      pathPrefix(JavaUUID) { toolTagId =>
        pathEndOrSingleSlash {
          get { getToolTag(toolTagId) } ~
            put { updateToolTag(toolTagId) } ~
            delete { deleteToolTag(toolTagId) }
        }
      }
  }

  def listToolTags: Route = authenticate { user =>
    (withPagination) { (page) =>
      complete {
        ToolTagDao.query
          .filter(user)
          .page(page, fr"")
          .transact(xa)
          .unsafeToFuture()
      }
    }
  }

  def createToolTag: Route = authenticate { user =>
    entity(as[ToolTag.Create]) { newToolTag =>
      onSuccess(
        ToolTagDao.insert(newToolTag, user).transact(xa).unsafeToFuture()) {
        toolTag =>
          complete(StatusCodes.Created, toolTag)
      }
    }
  }

  def getToolTag(toolTagId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ToolTagDao.query
        .ownedBy(user, toolTagId)
        .exists
        .transact(xa)
        .unsafeToFuture
    } {
      rejectEmptyResponse {
        complete(
          ToolTagDao.query.filter(toolTagId).select.transact(xa).unsafeToFuture)
      }
    }
  }

  def updateToolTag(toolTagId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ToolTagDao.query
        .ownedBy(user, toolTagId)
        .exists
        .transact(xa)
        .unsafeToFuture
    } {
      entity(as[ToolTag]) { updatedToolTag =>
        onSuccess(
          ToolTagDao
            .update(updatedToolTag, toolTagId, user)
            .transact(xa)
            .unsafeToFuture()) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteToolTag(toolTagId: UUID): Route = authenticate { user =>
    authorizeAsync {
      ToolTagDao.query
        .ownedBy(user, toolTagId)
        .exists
        .transact(xa)
        .unsafeToFuture
    } {
      onSuccess(
        ToolTagDao.query
          .filter(toolTagId)
          .delete
          .transact(xa)
          .unsafeToFuture()) {
        completeSingleOrNotFound
      }
    }
  }

}
