package com.rasterfoundry.batch.groundwork

import com.rasterfoundry.batch.Job
import com.rasterfoundry.notification.intercom.Model._
import com.rasterfoundry.notification.intercom._

import cats.effect.{Async, IO}
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend

object NotifyIntercomProgram extends Job {
  val name = "notify-intercom"

  val getBackend = for {
    backendRef <- Async.memoize {
      AsyncHttpClientCatsBackend[IO]()
    }
    backend <- backendRef
  } yield backend

  def runJob(args: List[String]): IO[Unit] = args match {
    case externalId +: msg +: Nil =>
      for {
        backend <- getBackend
        _ <- {
          val notifier = new LiveIntercomNotifier[IO](backend)
          notifier.notifyUser(
            Config.intercomToken,
            Config.intercomAdminId,
            ExternalId(externalId),
            Message(msg)
          )
        }
      } yield ()
    case _ =>
      IO.raiseError(
        new Exception(
          s"Arguments should match pattern `USER_ID MESSAGE`. Got $args"
        )
      )
  }
}
