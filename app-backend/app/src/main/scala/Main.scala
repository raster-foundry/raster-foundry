package com.azavea.rf

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

import com.azavea.rf.utils.Config
import com.azavea.rf.database.Database

object AkkaSystem {
  implicit val system = ActorSystem("rf-system")
  implicit val materializer = ActorMaterializer()

  trait LoggerExecutor {
    protected implicit val log = Logging(system, "app")
  }
}

object Main extends App with Config with Router with AkkaSystem.LoggerExecutor {

  import AkkaSystem._

  implicit lazy val database = Database.DEFAULT

  Http().bindAndHandle(routes, httpHost, httpPort)

}
