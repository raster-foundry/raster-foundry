package com.azavea.rf.tile

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._

object AkkaSystem {
  implicit val system = ActorSystem("rf-tiler-system")
  implicit val materializer = ActorMaterializer()

  trait LoggerExecutor {
    protected implicit val log = Logging(system, "tiler")
  }
}

object Main extends App with Config with AkkaSystem.LoggerExecutor {
  import AkkaSystem._

  def rootRoute = pathPrefix("tiles") { Routes.singleLayer }

  Http().bindAndHandle(rootRoute, httpHost, httpPort)
}
