package com.azavea.rf.tile

import com.azavea.rf.database.Database

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import geotrellis.spark.io._

object AkkaSystem {
  implicit val system = ActorSystem("rf-tiler-system")
  implicit val materializer = ActorMaterializer()

  trait LoggerExecutor {
    protected implicit val log = Logging(system, "tiler")
  }
}

object Main extends App with Config with AkkaSystem.LoggerExecutor {
  import AkkaSystem._

  val database = Database.DEFAULT

  def exceptionHandler =
    ExceptionHandler {
      case e: TileNotFoundError =>
        complete(StatusCodes.NotFound)
      case e: IllegalArgumentException =>
        complete(StatusCodes.ClientError(400)("Bad Argument", e.getMessage))
      case e: IllegalStateException =>
        complete(StatusCodes.ClientError(400)("Bad Request", e.getMessage))
    }

  def rootRoute = handleExceptions(exceptionHandler) {
    pathPrefix("tiles") {
      Routes.singleLayer ~ pathPrefix("healthcheck") {
        pathEndOrSingleSlash {
          get {
            complete {
              HttpResponse(StatusCodes.OK)
            }
          }
        }
      } ~
      Routes.singleLayer ~
      Routes.mosaicProject(database)
    }
  }

  Http().bindAndHandle(rootRoute, httpHost, httpPort)
}
