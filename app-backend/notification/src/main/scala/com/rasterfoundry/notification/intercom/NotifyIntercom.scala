package com.rasterfoundry.notification.intercom

import com.rasterfoundry.notification.intercom.Model._

import cats.effect.Sync
import cats.implicits._
import sttp.client._
import sttp.client.circe._
import sttp.model.Uri
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.Json
import io.circe.syntax._

trait IntercomNotifier[F[_]] {
  def notifyUser(
      intercomToken: IntercomToken,
      adminId: UserId,
      userId: ExternalId,
      msg: Message
  ): F[Unit]
}

class LiveIntercomNotifier[F[_]: Sync](
    implicit backend: SttpBackend[F, Nothing, NothingT]
) extends IntercomNotifier[F] {
  val sttpApiBase = "https://api.intercom.io"

  implicit val unsafeLoggerF = Slf4jLogger.getLogger[F]

  private def responseAsBody[T](
      resp: Either[ResponseError[io.circe.Error], T],
      fallback: => T
  ): F[T] =
    resp match {
      case Left(err) =>
        Logger[F].error(err)(err.getMessage) *>
          Sync[F].delay(fallback)
      case Right(body) =>
        Sync[F].delay(body)
    }

  def notifyUser(
      intercomToken: IntercomToken,
      adminId: UserId,
      userId: ExternalId,
      msg: Message
  ): F[Unit] = {
    val uri = Uri(java.net.URI.create(s"$sttpApiBase/messages"))
    val resp =
      Logger[F].debug(s"Notifying $userId") *>
        basicRequest.auth
          .bearer(intercomToken.underlying)
          .header("Accept", "application/json")
          .post(uri)
          .body(MessagePost(adminId, userId, msg))
          .response(asJson[Json])
          .send()

    (resp flatMap { r =>
      responseAsBody[Json](r.body, ().asJson)
    }).void
  }
}
