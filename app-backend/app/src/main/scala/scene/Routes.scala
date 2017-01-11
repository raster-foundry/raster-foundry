package com.azavea.rf.scene

import java.util.UUID

import scala.util.{Success, Failure}

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes

import com.azavea.rf.auth.Authentication
import com.azavea.rf.database.tables.Scenes
import com.azavea.rf.database.Database
import com.azavea.rf.datamodel._
import com.azavea.rf.utils.UserErrorHandler
import com.azavea.rf.utils.RfPaginationDirectives


trait SceneRoutes extends Authentication
    with SceneQueryParameterDirective
    with RfPaginationDirectives
    with UserErrorHandler {

  implicit def database: Database

  val sceneRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get { listScenes } ~
      post { createScene }
    } ~
    pathPrefix(JavaUUID) { sceneId =>
      pathEndOrSingleSlash {
        get { getScene(sceneId) } ~
        put { updateScene(sceneId) } ~
        delete { deleteScene(sceneId) }
      }
    }
  }

  def listScenes: Route = authenticate { user =>
    (withPagination & sceneQueryParameters) { (page, sceneParams) =>
      complete(Scenes.listScenes(page, sceneParams, user))
    }
  }

  def createScene: Route = authenticate { user =>
    entity(as[Scene.Create]) { newScene =>
      onSuccess(Scenes.insertScene(newScene, user)) { scene =>
        complete((StatusCodes.Created, scene))
      }
    }
  }

  def getScene(sceneId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        Scenes.getScene(sceneId)
      }
    }
  }

  def updateScene(sceneId: UUID): Route = authenticate { user =>
    entity(as[Scene]) { updatedScene =>
      onComplete(Scenes.updateScene(updatedScene, sceneId, user)) {
        case Success(result) => {
          result match {
            case 1 => complete(StatusCodes.NoContent)
            case count => throw new IllegalStateException(
              s"Error updating scene: update result expected to be 1, was $count"
            )
          }
        }
        case Failure(e) => throw e
      }
    }
  }

  def deleteScene(sceneId: UUID): Route = authenticate { user =>
    onSuccess(Scenes.deleteScene(sceneId)) {
      case 1 => complete(StatusCodes.NoContent)
      case 0 => complete(StatusCodes.NotFound)
      case count => throw new IllegalStateException(
        s"Error deleting scene: delete result expected to be 1, was $count"
      )
    }
  }
}
