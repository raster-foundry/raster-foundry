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
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._

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

  implicit lazy val xa = RFTransactor.xa
  val canSelect = sql"SELECT 1".query[Int].unique.transact(xa).unsafeRunSync
  println(s"Server Started (${canSelect})")

  def terminate(): Unit = {
    Try(system.terminate())
    Try(Kamon.shutdown())
  }

  sys.addShutdownHook {
    terminate()
  }

  Http().bindAndHandle(routes, httpHost, httpPort)
}
