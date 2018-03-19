package com.azavea.rf.tile

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.azavea.rf.database.util.RFTransactor
import kamon.Kamon

import scala.util.Try

object AkkaSystem {
  implicit val system = ActorSystem("rf-tiler-system")
  implicit val materializer = ActorMaterializer()
}

object Main extends App
  with Config {

  Kamon.start()

  import AkkaSystem._

  val router = new Router()

  implicit lazy val xa = RFTransactor.xa

  def terminate(): Unit = {
    Try(system.terminate())
    Try(Kamon.shutdown())
  }

  sys.addShutdownHook {
    terminate()
  }

  Http().bindAndHandle(router.root, httpHost, httpPort)
}
