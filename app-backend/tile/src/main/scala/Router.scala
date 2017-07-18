package com.azavea.rf.tile

import com.azavea.rf.database.Database
import com.azavea.rf.tile.routes._
import com.azavea.rf.tile.tool._

import ch.megard.akka.http.cors.CorsDirectives._
import ch.megard.akka.http.cors.CorsSettings
import akka.http.scaladsl.server._
import com.typesafe.scalalogging.LazyLogging

class Router extends LazyLogging
    with TileAuthentication
    with TileErrorHandler {

  implicit lazy val database = Database.DEFAULT
  implicit val system = AkkaSystem.system
  implicit val materializer = AkkaSystem.materializer

  implicit val defaultDespatcher = system.dispatchers.defaultGlobalDispatcher

  lazy val blockingMosaicRoutesDispatcher =
    system.dispatchers.lookup("blocking-io-dispatcher")
  lazy val blockingSceneRoutesDispatcher =
    system.dispatchers.lookup("blocking-io-dispatcher")
  lazy val blockingToolRoutesDispatcher =
    system.dispatchers.lookup("blocking-io-dispatcher")

  val toolRoutes = new ToolRoutes()

  val corsSettings = CorsSettings.defaultSettings

  def root = cors() {
    handleExceptions(tileExceptionHandler) {
      pathPrefix("tiles") {
        pathPrefix(JavaUUID) { projectId =>
          tileAccessAuthorized(projectId) {
            case true => MosaicRoutes.mosaicProject(projectId)(database, blockingMosaicRoutesDispatcher)
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
        tileAuthenticateOption { _ =>
          SceneRoutes.root(blockingSceneRoutesDispatcher)
        } ~
        pathPrefix("tools") {
          get {
            tileAuthenticateOption { _ =>
              TileSources.root(toolRoutes)(database, blockingToolRoutesDispatcher)
            }
          }
        }
      }
    }
  }
}
