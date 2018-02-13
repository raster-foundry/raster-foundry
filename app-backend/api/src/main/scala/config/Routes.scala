package com.azavea.rf.api.config

import akka.http.scaladsl.server.Route
import cats.effect.IO
import com.azavea.rf.api.Codec._
import com.azavea.rf.common.Authentication
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie.util.transactor.Transactor
import io.circe._
import io.circe.generic.auto._

trait ConfigRoutes extends Authentication {
  implicit def xa: Transactor[IO]
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
