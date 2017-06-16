package com.azavea.rf.tile.routes

import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.CirceSupport._

import scala.concurrent.ExecutionContext.Implicits.global


object HealthCheckRoute extends LazyLogging {
  def root: Route = complete {
    Map(
      "service" -> "tile",
      "status" -> "OK",
      "active threads" -> Thread.activeCount.toString
    )
  }
}
