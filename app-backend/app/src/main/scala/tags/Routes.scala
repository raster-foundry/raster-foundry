package com.azavea.rf.modeltag

import java.util.UUID

import scala.util.{Success, Failure}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.azavea.rf.auth.Authentication
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.ModelTags
import com.azavea.rf.datamodel._
import com.azavea.rf.utils.UserErrorHandler
import com.lonelyplanet.akka.http.extensions.PaginationDirectives

trait ModelTagRoutes extends Authentication with PaginationDirectives with UserErrorHandler {
  implicit def database: Database

  val modelTagRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listModelTags } ~
      post { createModelTag }
    } ~
    pathPrefix(JavaUUID) { modelTagId =>
      pathEndOrSingleSlash {
        get { getModelTag(modelTagId) } ~
        put { updateModelTag(modelTagId) } ~
        delete { deleteModelTag(modelTagId) }
      }
    }
  }

  def listModelTags: Route = authenticate { user =>
    (withPagination) { (page) =>
      complete {
        ModelTags.listModelTags(page)
      }
    }
  }

  def createModelTag: Route = authenticate { user =>
    entity(as[ModelTag.Create]) { newModelTag =>
      onSuccess(ModelTags.insertModelTag(newModelTag, user.id)) { modelTag =>
        complete(StatusCodes.Created, modelTag)
      }
    }
  }

  def getModelTag(modelTagId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete(ModelTags.getModelTag(modelTagId))
    }
  }

  def updateModelTag(modelTagId: UUID): Route = authenticate { user =>
    entity(as[ModelTag]) { updatedModelTag =>
      onComplete(ModelTags.updateModelTag(updatedModelTag, modelTagId, user)) {
        case Success(result) => {
          result match {
            case 1 => complete(StatusCodes.NoContent)
            case count => throw new IllegalStateException(
              s"Error updating model tag: update result expected to be 1, was $count"
            )
          }
        }
        case Failure(e) => throw e
      }
    }
  }

  def deleteModelTag(modelTagId: UUID): Route = authenticate { user =>
    onSuccess(ModelTags.deleteModelTag(modelTagId)) {
      case 1 => complete(StatusCodes.NoContent)
      case 0 => complete(StatusCodes.NotFound)
      case count => throw new IllegalStateException(
        s"Error deleting tag: delete result expected to be 1, was $count"
      )
    }
  }

}
