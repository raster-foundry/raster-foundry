package com.azavea.rf.tile.healthcheck

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.CirceSupport._

trait HealthCheckRoute extends LazyLogging {
  val healthCheckRoute: Route = get {
    complete {
      Map(
        "service" -> "tile",
        "status" -> "OK",
        "active threads" -> Thread.activeCount.toString
      )
    }
  }
}
