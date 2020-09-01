package com.rasterfoundry.notification.intercom

import com.rasterfoundry.notification.intercom.Model._

import cats.effect.Sync
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import sttp.client._
import sttp.client.circe._
import sttp.model.Uri

trait IntercomNotifier[F[_]] {
  def notifyUser(
      intercomToken: IntercomToken,
      adminId: UserId,
      userId: ExternalId,
      msg: Message
  ): F[Unit]
}

class LiveIntercomNotifier[F[_]: Sync](
    backend: SttpBackend[
      F,
      Nothing,
      sttp.client.asynchttpclient.WebSocketHandler
    ]
) extends IntercomNotifier[F] {
  val sttpApiBase = "https://api.intercom.io"

  implicit val unsafeLoggerF = Slf4jLogger.getLogger[F]
  implicit val sttpBackend = backend

  def notifyUser(
      intercomToken: IntercomToken,
      adminId: UserId,
      userId: ExternalId,
      msg: Message
  ): F[Unit] = {
    val uri = Uri(java.net.URI.create(s"$sttpApiBase/messages"))
    val resp =
      Logger[F].debug(s"Notifying $userId at $uri") *>
        basicRequest.auth
          .bearer(intercomToken.underlying)
          .header("Accept", "application/json")
          .post(uri)
          .body(MessagePost(adminId, userId, msg))
          .send()

    resp.void
  }
}
