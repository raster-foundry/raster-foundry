package com.rasterfoundry.api

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import cats.effect.{ContextShift, IO}
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.rasterfoundry.akkautil.RFRejectionHandler._
import com.rasterfoundry.api.utils.Config
import com.rasterfoundry.database.util.RFTransactor
import kamon.Kamon

import scala.util.Try
import doobie.hikari._
import doobie.hikari.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object AkkaSystem {
  implicit val system = ActorSystem("rf-system")
  implicit val materializer = ActorMaterializer()
}

object Main extends App with Config with Router {

  Kamon.start()

  implicit val system = AkkaSystem.system
  implicit val materializer = AkkaSystem.materializer

  val dbContextShift: ContextShift[IO] = IO.contextShift(
    ExecutionContext.fromExecutor(
      Executors.newFixedThreadPool(
        8,
        new ThreadFactoryBuilder().setNameFormat("db-client-%d").build()
      )
    ))

  val xa = RFTransactor.transactor(dbContextShift)

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
