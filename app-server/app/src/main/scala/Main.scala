package com.azavea.rf

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

import com.azavea.rf.utils.{Config, Database}

object Main extends App with Config with Router {

  implicit val system = ActorSystem("rf-system")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher
  implicit val database = new Database(jdbcUrl, dbUser, dbPassword)

  Http().bindAndHandle(routes, httpHost, httpPort)

}
