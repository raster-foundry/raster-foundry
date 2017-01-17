package com.azavea.rf.config

import akka.http.scaladsl.server.Route

import com.azavea.rf.authentication.Authentication


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
