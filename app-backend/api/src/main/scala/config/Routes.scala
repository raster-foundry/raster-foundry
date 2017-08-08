package com.azavea.rf.api.config

import akka.http.scaladsl.server.Route
import com.azavea.rf.api.Codec._
import com.azavea.rf.common.Authentication
import com.azavea.rf.database.Database
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe._
import io.circe.generic.auto._

trait ConfigRoutes extends Authentication {
  implicit def database: Database

  val configRoutes: Route = {
    pathEndOrSingleSlash {
      get {
        complete {
          AngularConfigService.getConfig()
        }
      }
    }
  }
}
