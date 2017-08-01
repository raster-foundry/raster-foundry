package com.azavea.rf.tile

import com.azavea.rf.common.cache.CacheClient
import com.azavea.rf.common.cache.kryo.KryoMemcachedClient
import com.azavea.rf.database.Database
import com.azavea.rf.tile.healthcheck.HealthCheckRoute
import com.azavea.rf.tile.projects.ProjectRoutes
import com.azavea.rf.tile.tools.ToolRoutes

import ch.megard.akka.http.cors.CorsDirectives._
import ch.megard.akka.http.cors.CorsSettings
import com.typesafe.scalalogging.LazyLogging
import geotrellis.spark.io.postgres.PostgresAttributeStore

class Router extends HealthCheckRoute
  with ProjectRoutes
  with ToolRoutes
  with LazyLogging
  with TileAuthentication
  with TileErrorHandler {

  implicit lazy val database = Database.DEFAULT
  val system = AkkaSystem.system
  implicit val materializer = AkkaSystem.materializer

  implicit lazy val blockingDispatcher =
    system.dispatchers.lookup("blocking-dispatcher")
  val memcachedClient: KryoMemcachedClient = KryoMemcachedClient.DEFAULT

  val rfCache: CacheClient = new CacheClient(memcachedClient)
  val store = PostgresAttributeStore()

  val corsSettings = CorsSettings.defaultSettings

  def root = cors() {
    handleExceptions(tileExceptionHandler) {
      pathPrefix("tiles") {
        projectRoutes ~ pathPrefix("healthcheck") {
          pathEndOrSingleSlash {
            healthCheckRoute
          }
        } ~
        pathPrefix("tools") {
          toolRoutes
        }
      }
    }
  }
}
