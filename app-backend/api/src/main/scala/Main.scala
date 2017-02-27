package com.azavea.rf.api

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

import com.azavea.rf.api.utils.Config
import com.azavea.rf.database.Database

object AkkaSystem {
  implicit val system = ActorSystem("rf-system")
  implicit val materializer = ActorMaterializer()

  trait LoggerExecutor {
    protected implicit val log = Logging(system, "app")
  }
}

object Main extends App with Config with Router with AkkaSystem.LoggerExecutor {
  implicit lazy val database = Database.DEFAULT
  implicit val system = AkkaSystem.system
  implicit val materializer = AkkaSystem.materializer

  Http().bindAndHandle(routes, httpHost, httpPort)

}
