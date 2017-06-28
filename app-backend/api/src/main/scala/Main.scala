package com.azavea.rf.api

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

import kamon.Kamon

import com.azavea.rf.api.utils.Config
import com.azavea.rf.common.RFRejectionHandler._
import com.azavea.rf.database.Database
import scala.util.Try

object AkkaSystem {
  implicit val system = ActorSystem("rf-system")
  implicit val materializer = ActorMaterializer()

  trait LoggerExecutor {
    protected implicit val log = Logging(system, "app")
  }
}

object Main extends App
    with Config
    with Router
    with AkkaSystem.LoggerExecutor {

  Kamon.start()

  implicit lazy val database = Database.DEFAULT
  implicit val system = AkkaSystem.system
  implicit val materializer = AkkaSystem.materializer

  def terminate(): Unit = {
    Try(system.terminate())
    Try(Kamon.shutdown())
  }

  sys.addShutdownHook {
    terminate()
  }

  Http().bindAndHandle(routes, httpHost, httpPort)

}
