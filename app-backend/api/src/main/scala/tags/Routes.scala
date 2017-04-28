package com.azavea.rf.api.tooltag

import com.azavea.rf.common.{Authentication, UserErrorHandler, CommonHandlers}
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.ToolTags
import com.azavea.rf.datamodel._

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import io.circe._
import de.heikoseeberger.akkahttpcirce.CirceSupport._

import scala.util.{Success, Failure}
import java.util.UUID


trait ToolTagRoutes extends Authentication
    with PaginationDirectives
    with CommonHandlers
    with UserErrorHandler {
  implicit def database: Database

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
        ToolTags.listToolTags(page, user)
      }
    }
  }

  def createToolTag: Route = authenticate { user =>
    entity(as[ToolTag.Create]) { newToolTag =>
      authorize(user.isInRootOrSameOrganizationAs(newToolTag)) {
        onSuccess(ToolTags.insertToolTag(newToolTag, user)) { toolTag =>
          complete(StatusCodes.Created, toolTag)
        }
      }
    }
  }

  def getToolTag(toolTagId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete(ToolTags.getToolTag(toolTagId, user))
    }
  }

  def updateToolTag(toolTagId: UUID): Route = authenticate { user =>
    entity(as[ToolTag]) { updatedToolTag =>
      authorize(user.isInRootOrSameOrganizationAs(updatedToolTag)) {
        onSuccess(ToolTags.updateToolTag(updatedToolTag, toolTagId, user)) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteToolTag(toolTagId: UUID): Route = authenticate { user =>
    onSuccess(ToolTags.deleteToolTag(toolTagId, user)) {
      completeSingleOrNotFound
    }
  }

}
