package com.azavea.rf.api.tooltag

import com.azavea.rf.common.{Authentication, CommonHandlers, UserErrorHandler}
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



trait ToolTagRoutes extends Authentication
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler {
  implicit def xa: Transactor[IO]

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
        ToolTagDao.query.ownerFilter(user).page(page).transact(xa).unsafeToFuture()
      }
    }
  }

  def createToolTag: Route = authenticate { user =>
    entity(as[ToolTag.Create]) { newToolTag =>
      authorize(user.isInRootOrSameOrganizationAs(newToolTag)) {
        onSuccess(ToolTagDao.insert(newToolTag, user).transact(xa).unsafeToFuture()) { toolTag =>
          complete(StatusCodes.Created, toolTag)
        }
      }
    }
  }

  def getToolTag(toolTagId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete(ToolTagDao.query.ownerFilter(user).filter(fr"id = ${toolTagId}").select.transact(xa).unsafeToFuture)
    }
  }

  def updateToolTag(toolTagId: UUID): Route = authenticate { user =>
    entity(as[ToolTag]) { updatedToolTag =>
      authorize(user.isInRootOrSameOrganizationAs(updatedToolTag)) {
        onSuccess(ToolTagDao.update(updatedToolTag, toolTagId, user).transact(xa).unsafeToFuture()) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteToolTag(toolTagId: UUID): Route = authenticate { user =>
    onSuccess(ToolTagDao.query.ownerFilter(user).filter(fr"id = ${toolTagId}").delete.transact(xa).unsafeToFuture()) {
      completeSingleOrNotFound
    }
  }

}
