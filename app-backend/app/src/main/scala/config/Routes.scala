package com.azavea.rf.config

import akka.http.scaladsl.server.Route
import scala.concurrent.ExecutionContext

import com.azavea.rf.auth.Authentication
import com.azavea.rf.database.Database

trait ConfigRoutes extends Authentication {
  implicit def database: Database
  implicit val ec: ExecutionContext

  def configRoutes: Route = {
    pathPrefix("config") {
      get {
        complete(AngularConfigService.getConfig())
      }
    }
  }
}
