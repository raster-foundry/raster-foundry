package com.azavea.rf.api.scene

import com.azavea.rf.common.{Authentication, UserErrorHandler, CommonHandlers}
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.Scenes
import com.azavea.rf.datamodel._

import com.lonelyplanet.akka.http.extensions.PaginationDirectives

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import io.circe._
import io.circe.syntax._
import io.circe.parser._
import io.circe.generic.auto._

import de.heikoseeberger.akkahttpcirce.CirceSupport._

import scala.util.{Success, Failure}
import java.util.UUID

trait SceneRoutes extends Authentication
    with SceneQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
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
      complete {
        Scenes.listScenes(page, sceneParams, user)
      }
    }
  }

  def createScene: Route = authenticate { user =>
    entity(as[Scene.Create]) { newScene =>
      authorize(user.isInRootOrSameOrganizationAs(newScene)) {
        onSuccess(Scenes.insertScene(newScene, user)) { scene =>
          complete((StatusCodes.Created, scene))
        }
      }
    }
  }

  def getScene(sceneId: UUID): Route = authenticate { user =>
    rejectEmptyResponse {
      complete {
        Scenes.getScene(sceneId, user)
      }
    }
  }

  def updateScene(sceneId: UUID): Route = authenticate { user =>
    entity(as[Scene]) { updatedScene =>
      authorize(user.isInRootOrSameOrganizationAs(updatedScene)) {
        onSuccess(Scenes.updateScene(updatedScene, sceneId, user)) {
          completeSingleOrNotFound
        }
      }
    }
  }

  def deleteScene(sceneId: UUID): Route = authenticate { user =>
    onSuccess(Scenes.deleteScene(sceneId, user)) {
      completeSingleOrNotFound
    }
  }
}
