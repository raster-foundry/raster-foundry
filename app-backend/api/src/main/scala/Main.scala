package com.azavea.rf.api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import cats.effect.IO
import com.azavea.rf.api.utils.Config
import com.azavea.rf.common.RFRejectionHandler._
import com.azavea.rf.database.util.RFTransactor
import kamon.Kamon

import scala.util.Try
import doobie.hikari._
import doobie.hikari.implicits._

import scala.concurrent.duration._

object AkkaSystem {
  implicit val system = ActorSystem("rf-system")
  implicit val materializer = ActorMaterializer()
}

object Main extends App
  with Config
  with Router {

  Kamon.start()

    implicit val system = AkkaSystem.system
  implicit val materializer = AkkaSystem.materializer

  // TODO: something here
  implicit lazy val xa = RFTransactor.xa

  def terminate(): Unit = {
    Try(system.terminate())
    Try(Kamon.shutdown())
  }

  sys.addShutdownHook {
    terminate()
  }

  Http().bindAndHandle(routes, httpHost, httpPort)
}
