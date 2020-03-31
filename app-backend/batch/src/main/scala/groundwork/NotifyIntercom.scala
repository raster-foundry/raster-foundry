package com.rasterfoundry.batch.groundwork

import com.rasterfoundry.batch.Job
import com.rasterfoundry.notification.intercom._
import com.rasterfoundry.notification.intercom.Model._

import cats.effect.IO
import com.softwaremill.sttp.asynchttpclient.cats.AsyncHttpClientCatsBackend

object NotifyIntercomProgram extends Job {
  val name = "notify-intercom"

  implicit val backend = AsyncHttpClientCatsBackend[IO]()

  def runJob(args: List[String]): IO[Unit] = args match {
    case externalId +: msg +: Nil =>
      val notifier = new LiveIntercomNotifier[IO]
      notifier.notifyUser(
        Config.intercomToken,
        Config.intercomAdminId,
        ExternalId(externalId),
        Message(msg)
      )
    case _ =>
      IO.raiseError(
        new Exception(
          s"Arguments should match pattern `USER_ID MESSAGE`. Got $args"
        )
      )
  }
}
