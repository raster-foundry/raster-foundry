package com.rasterfoundry.api.config

import com.rasterfoundry.akkautil.{Authentication, CommonHandlers}

import akka.http.scaladsl.server.Route
import cats.effect.IO
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.circe.generic.auto._

trait ConfigRoutes extends CommonHandlers with Authentication {
  val xa: Transactor[IO]
  val configRoutes: Route = {
    pathEndOrSingleSlash {
      get {
        complete {
          AngularConfigService.getConfig().transact(xa).unsafeToFuture
        }
      }
    }
  }
}
