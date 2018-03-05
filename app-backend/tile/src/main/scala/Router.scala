package com.azavea.rf.tile

import com.azavea.rf.common.CommonHandlers
import com.azavea.rf.tile.routes._
import com.azavea.rf.tile.tool._

import com.azavea.maml.serve.InterpreterExceptionHandling
import akka.http.scaladsl.server._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings._
import com.typesafe.scalalogging.LazyLogging
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats.effect.IO


class Router extends LazyLogging
    with TileAuthentication
    with CommonHandlers
    with InterpreterExceptionHandling
    with TileErrorHandler {

  implicit def xa: Transactor[IO]

  val system = AkkaSystem.system
  implicit val materializer = AkkaSystem.materializer

  lazy val blockingSceneRoutesDispatcher =
    system.dispatchers.lookup("blocking-dispatcher")

  val toolRoutes = new ToolRoutes()

  val corsSettings = CorsSettings.defaultSettings

  def root = cors() {
    handleExceptions(tileExceptionHandler) {
      pathPrefix(JavaUUID) { projectId =>
        projectTileAccessAuthorized(projectId) {
          case true => MosaicRoutes.mosaicProject(projectId)(xa)
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
                toolRoutes.tms(toolRunId, user) ~
                  toolRoutes.raw(toolRunId, user) ~
                  toolRoutes.validate(toolRunId, user) ~
                  toolRoutes.statistics(toolRunId, user) ~
                  toolRoutes.histogram(toolRunId, user) ~
                  toolRoutes.preflight(toolRunId, user)
              }
            }
          }
        }
      }
    }
  }
}
