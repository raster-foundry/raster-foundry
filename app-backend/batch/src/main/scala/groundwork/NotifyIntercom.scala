package com.rasterfoundry.batch.groundwork

import com.rasterfoundry.batch.groundwork.types._

import cats.effect.Sync
import cats.implicits._
import com.softwaremill.sttp._
import com.softwaremill.sttp.circe._
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
    implicit backend: SttpBackend[F, Nothing]
) extends IntercomNotifier[F] {
  val sttpApiBase = "https://api.intercom.io"

  implicit val unsafeLoggerF = Slf4jLogger.getLogger[F]

  private def responseAsBody[T](
      resp: Either[String, Either[DeserializationError[io.circe.Error], T]],
      fallback: => T
  ): F[T] =
    resp match {
      case Left(err) =>
        Logger[F].error(err) *>
          Sync[F].delay(fallback)
      case Right(deserialized) =>
        deserialized match {
          case Left(err) =>
            Logger[F].error(err.error)(err.message) *>
              Sync[F].delay(fallback)
          case Right(body) =>
            Sync[F].delay(body)
        }
    }

  def notifyUser(
      intercomToken: IntercomToken,
      adminId: UserId,
      userId: ExternalId,
      msg: Message
  ): F[Unit] = {
    val uri = Uri(java.net.URI.create(s"$sttpApiBase/messages"))
    val resp = sttp.auth
      .bearer(intercomToken.underlying)
      .header("Accept", MediaTypes.Json)
      .post(uri)
      .body(MessagePost(adminId, userId, msg))
      .response(asJson[Json])
      .send()

    (resp flatMap { r =>
      responseAsBody[Json](r.body, ().asJson)
    }).void
  }
}
