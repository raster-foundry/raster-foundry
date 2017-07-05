package com.azavea.rf.tile

import com.azavea.rf.database.Database
import com.azavea.rf.tile.routes.SceneRoutes

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import geotrellis.spark.io._
import kamon.Kamon

import scala.util.Try

object AkkaSystem {
  implicit val system = ActorSystem("rf-tiler-system")
  implicit val materializer = ActorMaterializer()

  trait LoggerExecutor {
    protected implicit val log = Logging(system, "tiler")
  }
}

object Main extends App
  with Config
  with AkkaSystem.LoggerExecutor {

  Kamon.start()

  import AkkaSystem._
  val database = Database.DEFAULT
  val router = new Router()

  def terminate(): Unit = {
    Try(system.terminate())
    Try(Kamon.shutdown())
  }

  sys.addShutdownHook {
    terminate()
  }

  Http().bindAndHandle(router.root, httpHost, httpPort)
}
