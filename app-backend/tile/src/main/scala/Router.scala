package com.azavea.rf.tile

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server._
import com.azavea.rf.database.Database
import com.azavea.rf.tile.routes._
import com.azavea.rf.tile.tool._
import com.typesafe.scalalogging.LazyLogging

class Router extends LazyLogging
    with TileAuthentication
    with TileErrorHandler {

  implicit lazy val database = Database.DEFAULT
  implicit val system = AkkaSystem.system
  implicit val materializer = AkkaSystem.materializer

  val toolRoutes = new ToolRoutes()

  def root = handleExceptions(tileExceptionHandler) {
    pathPrefix("tiles") {
      pathPrefix("healthcheck") {
        pathEndOrSingleSlash {
          get {
            complete {
              HttpResponse(StatusCodes.OK)
            }
          }
        }
      } ~
      tileAuthenticateOption { _ =>
        SceneRoutes.root ~
        pathPrefix("tools") {
          toolRoutes.root(TileSources.cachedTmsSource)
        }
      } ~
      pathPrefix(JavaUUID) { projectId =>
        tileAccessAuthorized(projectId) {
          case true => MosaicRoutes.mosaicProject(projectId)(database)
          case _ => reject(AuthorizationFailedRejection)
        }
      }
    }
  }
}
