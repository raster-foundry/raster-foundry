package com.azavea.rf.config

import akka.http.scaladsl.server.Route
import scala.concurrent.ExecutionContext

import com.azavea.rf.auth.Authentication
import com.azavea.rf.database.Database

trait ConfigRoutes extends Authentication {
  def configRoutes: Route = {
    pathEndOrSingleSlash {
      get {
        complete {
          AngularConfigService.getConfig()
        }
      }
    }
  }
}
