package com.azavea.rf.tile

import akka.http.scaladsl.server._
import ch.megard.akka.http.cors.CorsDirectives._
import ch.megard.akka.http.cors.CorsSettings
import com.azavea.rf.common.CommonHandlers
import com.azavea.rf.common.ast.InterpreterExceptionHandling
import com.azavea.rf.database.Database
import com.azavea.rf.tile.routes._
import com.azavea.rf.tile.tool._
import com.typesafe.scalalogging.LazyLogging

class Router extends LazyLogging
    with TileAuthentication
    with CommonHandlers
    with InterpreterExceptionHandling
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
          projectTileAccessAuthorized(projectId) {
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
            (handleExceptions(interpreterExceptionHandler) & handleExceptions(circeDecodingError)) {
              pathPrefix(JavaUUID) { (toolRunId) =>
                authenticateToolTileRoutes(toolRunId) { user =>
                  toolRoutes.tms(toolRunId, user, TileSources.cachedTmsSource) ~
                    toolRoutes.validate(toolRunId, user) ~
                    toolRoutes.histogram(toolRunId, user) ~
                    toolRoutes.preflight(toolRunId, user) ~
                    toolRoutes.statistics(toolRunId, user)
                }
              }
            }
          }
        }
      }
    }
  }
}
