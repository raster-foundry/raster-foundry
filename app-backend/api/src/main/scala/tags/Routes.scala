package com.azavea.rf.api.tooltag

import com.azavea.rf.common.{Authentication, UserErrorHandler}
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.ToolTags
import com.azavea.rf.datamodel._

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import io.circe._
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.CirceSupport._

import scala.util.{Success, Failure}
import java.util.UUID


trait ToolTagRoutes extends Authentication with PaginationDirectives with UserErrorHandler {
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
        ToolTags.listToolTags(page)
      }
    }
  }

  def createToolTag: Route = authenticate { user =>
    entity(as[ToolTag.Create]) { newToolTag =>
      onSuccess(ToolTags.insertToolTag(newToolTag, user.id)) { toolTag =>
        complete(StatusCodes.Created, toolTag)
      }
    }
  }

  def getToolTag(toolTagId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete(ToolTags.getToolTag(toolTagId))
    }
  }

  def updateToolTag(toolTagId: UUID): Route = authenticate { user =>
    entity(as[ToolTag]) { updatedToolTag =>
      onComplete(ToolTags.updateToolTag(updatedToolTag, toolTagId, user)) {
        case Success(result) => {
          result match {
            case 1 => complete(StatusCodes.NoContent)
            case count => throw new IllegalStateException(
              s"Error updating tool tag: update result expected to be 1, was $count"
            )
          }
        }
        case Failure(e) => throw e
      }
    }
  }

  def deleteToolTag(toolTagId: UUID): Route = authenticate { user =>
    onSuccess(ToolTags.deleteToolTag(toolTagId)) {
      case 1 => complete(StatusCodes.NoContent)
      case 0 => complete(StatusCodes.NotFound)
      case count => throw new IllegalStateException(
        s"Error deleting tag: delete result expected to be 1, was $count"
      )
    }
  }

}
