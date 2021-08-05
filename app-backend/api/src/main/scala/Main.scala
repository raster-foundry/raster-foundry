package com.rasterfoundry.api

import com.rasterfoundry.akkautil.RFRejectionHandler.rfRejectionHandler
import com.rasterfoundry.api.utils.{Config, IntercomNotifications}
import com.rasterfoundry.database.util.RFTransactor

import akka.actor.{ActorSystem, Terminated}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.server.RejectionHandler
import cats.effect.{ContextShift, IO}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings._
import com.google.common.util.concurrent.ThreadFactoryBuilder
import doobie.implicits._
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend

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

  val xa = RFTransactor.buildTransactor()

  val notifier =
    AsyncHttpClientCatsBackend[IO]() map { backend =>
      new IntercomNotifications(backend, xa)
    }

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

  val corsSettings = CorsSettings.defaultSettings.copy(
    allowedMethods =
      scala.collection.immutable.Seq(GET, POST, PUT, HEAD, OPTIONS, DELETE)
  )

  val corsRoutes = cors(corsSettings) {
    val rejectionHandler: RejectionHandler = RejectionHandler.default

    // rfRejectionHandler is responsible for handling circe rejections and transforming
    // the messages into slightly more human-readable text.
    // the default handler is responsible for making sure that headers survive
    // from the initial request, so for example CORS headers are still present on the
    // response if needed.
    handleRejections(rejectionHandler)(
      handleRejections(rfRejectionHandler)(routes)
    )
  }

  Http().newServerAt(httpHost, httpPort).bindFlow(corsRoutes)
}
