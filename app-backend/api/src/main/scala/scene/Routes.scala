package com.azavea.rf.api.scene

import com.azavea.rf.common.{Airflow, Authentication, UserErrorHandler, CommonHandlers}
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.Scenes
import com.azavea.rf.datamodel._


import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import io.circe._
import io.circe.syntax._
import io.circe.parser._
import kamon.akka.http.KamonTraceDirectives
import de.heikoseeberger.akkahttpcirce.CirceSupport._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}
import java.util.UUID

trait SceneRoutes extends Authentication
    with SceneQueryParameterDirective
    with PaginationDirectives
    with CommonHandlers
    with Airflow
    with UserErrorHandler
    with KamonTraceDirectives {

  implicit def database: Database

  val sceneRoutes: Route = handleExceptions(userExceptionHandler) {
    pathEndOrSingleSlash {
      get {
        traceName("scenes-list") {
        listScenes }
      } ~
      post {
        traceName("scenes-create") {
        createScene }
      }
    } ~
    pathPrefix(JavaUUID) { sceneId =>
      pathEndOrSingleSlash {
        get {
          traceName("scenes-detail") {
          getScene(sceneId)
          }
        } ~
        put { traceName("scenes-update") {
          updateScene(sceneId) }
        } ~
        delete { traceName("scenes-delete") {
          deleteScene(sceneId) }
        }
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
          if (scene.statusFields.ingestStatus == IngestStatus.ToBeIngested) kickoffSceneIngest(scene.id)
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
        onSuccess(Scenes.updateScene(updatedScene, sceneId, user)) { (result, kickoffIngest) =>
          if (kickoffIngest) kickoffSceneIngest(sceneId)
          completeSingleOrNotFound(result)
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
