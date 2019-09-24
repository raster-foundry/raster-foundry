package com.rasterfoundry.api

import java.util.concurrent.Executors

import akka.actor.{ActorSystem, Terminated}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import cats.effect.{ContextShift, IO}
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.rasterfoundry.akkautil.RFRejectionHandler._
import com.rasterfoundry.api.utils.Config
import com.rasterfoundry.database.util.RFTransactor
import doobie.implicits._

import scala.concurrent.{ExecutionContext, Future}

object AkkaSystem {
  implicit val system = ActorSystem("rf-system")
  implicit val materializer = ActorMaterializer()
}

object Main extends App with Config with Router {

  implicit val system = AkkaSystem.system
  implicit val materializer = AkkaSystem.materializer

  implicit val contextShift: ContextShift[IO] =
    IO.contextShift(
      ExecutionContext.fromExecutor(
        Executors.newCachedThreadPool(
          new ThreadFactoryBuilder().setNameFormat("db-transactor-%d").build()
        )
      )
    )

  val xa = RFTransactor.buildTransactor()
  implicit val ec = ExecutionContext.Implicits.global

  val canSelect = sql"SELECT 1".query[Int].unique.transact(xa).unsafeRunSync
  logger.info(s"Server Started (${canSelect})")

  def terminate(): Future[Terminated] = {
    system.terminate()
  }

  sys.addShutdownHook {
    terminate()
    ()
  }

  Http().bindAndHandle(routes, httpHost, httpPort)
}
