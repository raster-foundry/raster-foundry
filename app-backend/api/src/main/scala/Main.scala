package com.rasterfoundry.api

import com.rasterfoundry.akkautil.RFRejectionHandler._
import com.rasterfoundry.api.utils.{Config, IntercomNotifications}
import com.rasterfoundry.database.util.RFTransactor

import akka.actor.{ActorSystem, Terminated}
import akka.http.scaladsl.Http
import cats.effect.{Async, ContextShift, IO}
import com.google.common.util.concurrent.ThreadFactoryBuilder
import doobie.implicits._
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend

import scala.concurrent.{ExecutionContext, Future}

import java.util.concurrent.Executors

object AkkaSystem {
  implicit val system = ActorSystem("rf-system")
}

object Main extends App with Config with Router {

  implicit val system = AkkaSystem.system

  implicit val contextShift: ContextShift[IO] =
    IO.contextShift(
      ExecutionContext.fromExecutor(
        Executors.newCachedThreadPool(
          new ThreadFactoryBuilder().setNameFormat("db-transactor-%d").build()
        )
      )
    )

  val notifier = for {
    notifierIO <- Async.memoize(AsyncHttpClientCatsBackend[IO]() map {
      backend =>
        new IntercomNotifications(backend)
    })
    notifier <- notifierIO
  } yield notifier

  val xa = RFTransactor.buildTransactor()
  implicit val ec = ExecutionContext.Implicits.global

  val commonLabelClassGroupRoutes = new CommonLabelClassGroupRoutes(xa, ec)
  val commonLabelClassRoutes = new CommonLabelClassRoutes(xa, ec)

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
