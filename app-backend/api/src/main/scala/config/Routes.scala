package com.azavea.rf.api.config

import akka.http.scaladsl.server.Route

import com.azavea.rf.common.{Authentication}
import com.azavea.rf.database.Database
import io.circe._
import com.azavea.rf.database.tables.OrgFeatureFlags
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.CirceSupport._

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
