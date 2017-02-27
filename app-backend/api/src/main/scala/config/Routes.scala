package com.azavea.rf.api.config

import akka.http.scaladsl.server.Route

import com.azavea.rf.common.Authentication


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
