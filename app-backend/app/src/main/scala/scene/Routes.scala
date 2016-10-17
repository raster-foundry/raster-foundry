package com.azavea.rf.scene

import java.sql.Timestamp

import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes

import com.lonelyplanet.akka.http.extensions.PaginationDirectives
import com.azavea.rf.auth.Authentication
import com.azavea.rf.datamodel._
import com.azavea.rf.database.tables._
import com.azavea.rf.database.Database
import com.azavea.rf.utils.{UnmarshallWithExtraJson, UserErrorHandler}
import spray.json._
import java.util.UUID

trait SceneRoutes extends Authentication
    with SceneQueryParameterDirective
    with PaginationDirectives
    with UserErrorHandler {

  implicit def database: Database
  implicit val ec: ExecutionContext

  def sceneRoutes: Route = {
    handleExceptions(userExceptionHandler) {
      pathPrefix("api" / "scenes") {
        pathEndOrSingleSlash {
          authenticateAndAllowAnonymous { user =>
            withPagination { page =>
              get {
                sceneQueryParameters { sceneParams =>
                  onSuccess(Scenes.getScenes(page, sceneParams)) { scenes =>
                    complete(scenes)
                  }
                }
              }
            }
          } ~
          authenticate { user =>
            post {
              entity(as[Scene.Create]) { newScene =>
                onComplete(Scenes.insertScene(newScene, user)) {
                  case Success(scene) => complete(scene)
                  case Failure(_) => complete(StatusCodes.InternalServerError)
                }
              }
            }
          }
        } ~
        pathPrefix(JavaUUID) {sceneId =>
          pathEndOrSingleSlash {
            authenticateAndAllowAnonymous { user =>
              get {
                onSuccess(Scenes.getScene(sceneId)) {
                  case Some(scene) => complete(scene)
                  case _ => complete(StatusCodes.NotFound)
                }
              }
            } ~
            authenticate { user =>
              put {
                entity(as[Scene]) { updatedScene =>
                  onComplete(Scenes.updateScene(updatedScene, sceneId, user)) {
                    case Success(result) => {
                      result match {
                        case 1 => complete(StatusCodes.NoContent)
                        case count: Int => throw new Exception(
                          s"Error updating scene: update result expected to be 1, was $count"
                        )
                      }
                    }
                    case Failure(e) => throw e
                  }
                }
              } ~
              delete {
                onSuccess(Scenes.deleteScene(sceneId)) {
                  case 1 => complete(StatusCodes.NoContent)
                  case 0 => complete(StatusCodes.NotFound)
                  case _ => complete(StatusCodes.InternalServerError)
                }
              }
            }
          }
        }
      }
    }
  }
}
