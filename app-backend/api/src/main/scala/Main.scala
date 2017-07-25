package com.azavea.rf.api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.azavea.rf.api.utils.Config
import com.azavea.rf.common.RFRejectionHandler._
import com.azavea.rf.database.Database
import kamon.Kamon

import scala.util.Try

object AkkaSystem {
  implicit val system = ActorSystem("rf-system")
  implicit val materializer = ActorMaterializer()
}

object Main extends App
  with Config
  with Router {

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
