package com.azavea.rf.tile

import com.azavea.rf.database.Database
import com.azavea.rf.tile.routes._
import com.azavea.rf.tile.tool._

import ch.megard.akka.http.cors.CorsDirectives._
import ch.megard.akka.http.cors.CorsSettings
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server._
import com.typesafe.scalalogging.LazyLogging

class Router extends LazyLogging
    with TileAuthentication
    with TileErrorHandler {

  implicit lazy val database = Database.DEFAULT
  val system = AkkaSystem.system
  implicit val materializer = AkkaSystem.materializer

  lazy val blockingSceneRoutesDispatcher =
    system.dispatchers.lookup("blocking-dispatcher")

  val toolRoutes = new ToolRoutes()

  val corsSettings = CorsSettings.defaultSettings

  def root = cors() {
    handleExceptions(tileExceptionHandler) {
      pathPrefix("tiles") {
        pathPrefix(JavaUUID) { projectId =>
          tileAccessAuthorized(projectId) {
            case true => MosaicRoutes.mosaicProject(projectId)(database)
            case _ => reject(AuthorizationFailedRejection)
          }
        } ~
        pathPrefix("healthcheck") {
          pathEndOrSingleSlash {
            get {
              HealthCheckRoute.root
            }
          }
        } ~
        pathPrefix("tools") {
          get {
            tileAuthenticateOption { _ =>
              toolRoutes.tms(TileSources.cachedTmsSource) ~
              toolRoutes.validate ~
              toolRoutes.histogram ~
              toolRoutes.preflight
            }
          }
        }
      }
    }
  }
}
